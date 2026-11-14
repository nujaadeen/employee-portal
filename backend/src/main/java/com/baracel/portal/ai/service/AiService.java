package com.baracel.portal.ai.service;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.ai.domain.*;
import com.baracel.portal.ai.repo.*;
import com.baracel.portal.ai.service.AiAssistantPort.*;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.AuditService;
import com.baracel.portal.common.web.ApiException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {
    public record SourceDto(String title, String collection) {}
    public record DraftDto(UUID id, String kind, String headline, String caption, List<String> hashtags, List<String> suggestedChannels) {}
    public record MessageDto(UUID id, String role, String content, Instant createdAt, List<SourceDto> sources, @Nullable DraftDto draft) {}
    public record ConversationDto(UUID id, String title, Instant updatedAt) {}
    public record ConversationDetail(UUID id, String title, List<MessageDto> messages) {}
    public record Exchange(MessageDto userMessage, MessageDto assistantMessage) {}
    public record CollectionDto(String code, String name, int docCount, boolean allowed) {}
    public record ChannelDto(String code, String name, String type, String provider, boolean connected, boolean canPublish) {}
    public record AiAccess(List<String> roles, List<CollectionDto> collections, List<ChannelDto> channels, int companyChannels,
                           int personalChannels) {}
    public record PublishResult(String channel, String status) {}

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final AiMessageSourceRepository sources;
    private final AiDraftRepository drafts;
    private final KnowledgeCollectionRepository collections;
    private final SocialChannelRepository channels;
    private final PublishLogRepository publishLog;
    private final AiAssistantPort assistant;
    private final AuditService audit;

    public AiService(AiConversationRepository conversations, AiMessageRepository messages, AiMessageSourceRepository sources,
                     AiDraftRepository drafts, KnowledgeCollectionRepository collections, SocialChannelRepository channels,
                     PublishLogRepository publishLog, AiAssistantPort assistant, AuditService audit) {
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
        this.drafts = drafts;
        this.collections = collections;
        this.channels = channels;
        this.publishLog = publishLog;
        this.assistant = assistant;
        this.audit = audit;
    }

    /** Computed only here, from the caller's permissions. The assistant never decides access. */
    @Transactional(readOnly = true)
    public List<String> allowedCollections(CurrentUser me) {
        return collections.findAllByOrderBySortOrderAsc().stream().filter(c -> me.has(c.permission)).map(c -> c.code).toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> list(CurrentUser me) {
        return conversations.findByOwnerIdOrderByUpdatedAtDesc(me.employeeId()).stream()
                .map(c -> new ConversationDto(c.id, c.title, c.updatedAt)).toList();
    }

    @Transactional
    public ConversationDto create(CurrentUser me, String title) {
        AiConversation c = new AiConversation();
        c.id = UUID.randomUUID();
        c.ownerId = me.employeeId();
        c.title = title == null || title.isBlank() ? "New chat" : title.trim();
        conversations.save(c);
        return new ConversationDto(c.id, c.title, c.updatedAt);
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(CurrentUser me, UUID id) {
        AiConversation c = owned(me, id);
        return new ConversationDetail(c.id, c.title, messageDtos(messages.findByConversationIdOrderByCreatedAtAsc(id)));
    }

    @Transactional
    public Exchange send(CurrentUser me, UUID conversationId, String text) {
        AiConversation c = owned(me, conversationId);
        if (text == null || text.isBlank()) throw ApiException.badRequest("validation", "message is required");
        List<AiTurn> history = messages.findByConversationIdOrderByCreatedAtAsc(c.id).stream().map(m -> new AiTurn(m.role, m.content)).toList();

        AiMessage user = message(c.id, "USER", text.trim(), Instant.now());
        AiReply reply = assistant.chat(new AiRequest(c.id, text.trim(), history,
                new AiUser(me.employeeId(), me.name(), me.roles().stream().sorted().toList()), allowedCollections(me)));
        AiMessage bot = message(c.id, "ASSISTANT", reply.text(), Instant.now().plusMillis(1));
        for (AiSource s : reply.sources()) {
            AiMessageSource ms = new AiMessageSource();
            ms.messageId = bot.id;
            ms.title = s.title();
            ms.collection = s.collection();
            sources.save(ms);
        }
        DraftDto draft = null;
        if (reply.draft() != null) {
            AiDraft d = new AiDraft();
            d.id = UUID.randomUUID();
            d.messageId = bot.id;
            d.kind = reply.draft().kind();
            d.headline = reply.draft().headline();
            d.caption = reply.draft().caption();
            d.hashtags = String.join(" ", reply.draft().hashtags());
            d.suggestedChannels = String.join(",", reply.draft().suggestedChannels());
            drafts.save(d);
            draft = draftDto(d);
        }
        if ("New chat".equals(c.title)) c.title = text.trim().length() > 60 ? text.trim().substring(0, 60) : text.trim();
        c.updatedAt = Instant.now();
        return new Exchange(new MessageDto(user.id, "USER", user.content, user.createdAt, List.of(), null),
                new MessageDto(bot.id, "ASSISTANT", bot.content, bot.createdAt,
                        reply.sources().stream().map(s -> new SourceDto(s.title(), s.collection())).toList(), draft));
    }

    @Transactional(readOnly = true)
    public AiAccess access(CurrentUser me) {
        List<String> allowed = allowedCollections(me);
        List<CollectionDto> cols = collections.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new CollectionDto(c.code, c.name, c.docCount, allowed.contains(c.code))).toList();
        List<ChannelDto> chans = channels.findAllByOrderByIdAsc().stream()
                .filter(c -> "COMPANY".equals(c.type) || me.employeeId().equals(c.ownerId))
                .map(c -> new ChannelDto(c.code, c.name, c.type, c.provider, c.connected, canPublish(me, c))).toList();
        return new AiAccess(me.roles().stream().sorted().toList(), cols, chans,
                (int) chans.stream().filter(c -> "COMPANY".equals(c.type()) && c.connected()).count(),
                (int) chans.stream().filter(c -> "PERSONAL".equals(c.type()) && c.connected()).count());
    }

    /** Needs social:publish:company or social:publish:personal depending on the channel. Phase 1 only logs (SIMULATED). */
    @Transactional
    public List<PublishResult> publish(CurrentUser me, UUID draftId, List<String> channelCodes) {
        AiDraft d = drafts.findById(draftId).orElseThrow(() -> ApiException.notFound("Draft not found"));
        AiMessage m = messages.findById(d.messageId).orElseThrow();
        owned(me, m.conversationId);
        if (channelCodes == null || channelCodes.isEmpty()) throw ApiException.badRequest("validation", "Pick at least one channel.");
        Map<String, SocialChannel> byCode = channels.findByCodeIn(channelCodes).stream().collect(Collectors.toMap(c -> c.code, c -> c));
        for (String code : channelCodes) {
            SocialChannel c = byCode.get(code);
            if (c == null) throw ApiException.badRequest("unknown-channel", "Unknown channel: " + code);
            if (!canPublish(me, c)) throw ApiException.forbidden("You cannot publish to " + c.name + ".");
            if (!c.connected) throw ApiException.conflict("channel-not-connected", c.name + " is not connected.");
        }
        List<PublishResult> out = new ArrayList<>();
        for (String code : new LinkedHashSet<>(channelCodes)) {
            PublishLog log = new PublishLog();
            log.draftId = d.id;
            log.channelCode = code;
            log.userId = me.employeeId();
            log.userName = me.name();
            log.status = "SIMULATED";
            publishLog.save(log);
            out.add(new PublishResult(code, "SIMULATED"));
        }
        audit.log(me, "AI_DRAFT_PUBLISHED", "ai-draft", d.id.toString(), "channels=" + channelCodes + " (simulated)");
        return out;
    }

    // ---- helpers ----

    private boolean canPublish(CurrentUser me, SocialChannel c) {
        if ("COMPANY".equals(c.type)) return me.has("social:publish:company");
        return me.has("social:publish:personal") && me.employeeId().equals(c.ownerId);
    }

    private AiConversation owned(CurrentUser me, UUID id) {
        return conversations.findById(id).filter(c -> c.ownerId.equals(me.employeeId()))
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
    }

    private AiMessage message(UUID conversationId, String role, String content, Instant at) {
        AiMessage m = new AiMessage();
        m.id = UUID.randomUUID();
        m.conversationId = conversationId;
        m.role = role;
        m.content = content;
        m.createdAt = at;
        return messages.save(m);
    }

    private List<MessageDto> messageDtos(List<AiMessage> list) {
        List<UUID> ids = list.stream().map(m -> m.id).toList();
        Map<UUID, List<SourceDto>> src = ids.isEmpty() ? Map.of() : sources.findByMessageIdIn(ids).stream()
                .collect(Collectors.groupingBy(s -> s.messageId, Collectors.mapping(s -> new SourceDto(s.title, s.collection), Collectors.toList())));
        Map<UUID, DraftDto> dr = ids.isEmpty() ? Map.of() : drafts.findByMessageIdIn(ids).stream()
                .collect(Collectors.toMap(d -> d.messageId, this::draftDto));
        return list.stream().map(m -> new MessageDto(m.id, m.role, m.content, m.createdAt, src.getOrDefault(m.id, List.of()), dr.get(m.id))).toList();
    }

    private DraftDto draftDto(AiDraft d) {
        return new DraftDto(d.id, d.kind, d.headline, d.caption,
                d.hashtags.isBlank() ? List.of() : Arrays.asList(d.hashtags.split(" ")),
                d.suggestedChannels.isBlank() ? List.of() : Arrays.asList(d.suggestedChannels.split(",")));
    }
}

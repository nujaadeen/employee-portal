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
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiService {
    public record SourceDto(String title, String collection, @Nullable Integer page, @Nullable String sourceRef) {}
    public record DraftDto(UUID id, String kind, String headline, String caption, List<String> hashtags, List<String> suggestedChannels,
                           String platform, String language) {}
    public record MessageDto(UUID id, String role, String content, Instant createdAt, String status, List<SourceDto> sources, @Nullable DraftDto draft) {}
    public record ConversationDto(UUID id, String title, Instant updatedAt) {}
    public record ConversationDetail(UUID id, String title, List<MessageDto> messages) {}
    public record Exchange(MessageDto userMessage, MessageDto assistantMessage) {}
    public record CollectionDto(String code, String name, int docCount, boolean allowed) {}
    public record ChannelDto(String code, String name, String type, String provider, boolean connected, boolean canPublish) {}
    public record AiAccess(List<String> roles, List<CollectionDto> collections, List<ChannelDto> channels, int companyChannels,
                           int personalChannels) {}
    public record PublishResult(String channel, String status) {}
    public record FeedbackDto(UUID messageId, String rating, @Nullable String comment) {}
    /** What a chat call needs between saving the question and saving the answer. */
    public record PreparedChat(UUID conversationId, AiRequest request, MessageDto userMessage, boolean firstMessage, String text) {}

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final AiMessageSourceRepository sources;
    private final AiDraftRepository drafts;
    private final KnowledgeCollectionRepository collections;
    private final SocialChannelRepository channels;
    private final PublishLogRepository publishLog;
    private final AiAssistantPort assistant;
    private final AuditService audit;
    private final AiFeedbackRepository feedback;
    private final ChatRateLimiter rateLimiter;
    private final TransactionTemplate tx;

    public AiService(AiConversationRepository conversations, AiMessageRepository messages, AiMessageSourceRepository sources,
                     AiDraftRepository drafts, KnowledgeCollectionRepository collections, SocialChannelRepository channels,
                     PublishLogRepository publishLog, AiAssistantPort assistant, AuditService audit, AiFeedbackRepository feedback,
                     ChatRateLimiter rateLimiter, TransactionTemplate tx) {
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
        this.drafts = drafts;
        this.collections = collections;
        this.channels = channels;
        this.publishLog = publishLog;
        this.assistant = assistant;
        this.audit = audit;
        this.feedback = feedback;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
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

    /** JSON chat (no streaming). The AI call runs outside any database transaction. */
    public Exchange send(CurrentUser me, UUID conversationId, String text) {
        rateLimiter.check(me.employeeId());
        PreparedChat p = prepare(me, conversationId, text);
        AiReply reply;
        try {
            reply = assistant.chat(p.request());
        } catch (RuntimeException e) {
            savePartial(p, "", "FAILED");
            throw e;
        }
        return complete(p, reply);
    }

    /** Saves the question and builds the request: allowed collections from the caller's permissions, the last 10 messages, the user. */
    public PreparedChat prepare(CurrentUser me, UUID conversationId, String text) {
        if (text == null || text.isBlank()) throw ApiException.badRequest("validation", "message is required");
        String clean = text.trim();
        return tx.execute(st -> {
            AiConversation c = owned(me, conversationId);
            List<AiMessage> all = messages.findByConversationIdOrderByCreatedAtAsc(c.id);
            List<AiTurn> history = all.stream().skip(Math.max(0, all.size() - 10)).map(m -> new AiTurn(m.role, m.content)).toList();
            AiMessage user = message(c.id, "USER", clean, Instant.now(), "COMPLETE");
            AiRequest req = new AiRequest(c.id, clean, history, new AiUser(me.employeeId(), me.name(), me.roles().stream().sorted().toList()),
                    allowedCollections(me));
            return new PreparedChat(c.id, req, new MessageDto(user.id, "USER", user.content, user.createdAt, "COMPLETE", List.of(), null),
                    "New chat".equals(c.title), clean);
        });
    }

    /** Saves the assistant message with its sources and draft. */
    public Exchange complete(PreparedChat p, AiReply reply) {
        return tx.execute(st -> {
            AiMessage bot = message(p.conversationId(), "ASSISTANT", reply.text(), Instant.now().plusMillis(1), "COMPLETE");
            List<SourceDto> srcDtos = new ArrayList<>();
            for (AiSource s : reply.sources()) {
                AiMessageSource ms = new AiMessageSource();
                ms.messageId = bot.id;
                ms.title = trunc(s.title(), 200);
                ms.collection = s.collection();
                ms.page = s.page();
                ms.sourceRef = trunc(s.sourceRef(), 1000);
                sources.save(ms);
                srcDtos.add(new SourceDto(ms.title, ms.collection, ms.page, ms.sourceRef));
            }
            DraftDto draft = null;
            if (reply.draft() != null) {
                AiDraft d = new AiDraft();
                d.id = UUID.randomUUID();
                d.messageId = bot.id;
                d.kind = reply.draft().kind();
                d.headline = trunc(reply.draft().headline(), 200);
                d.caption = reply.draft().caption();
                d.hashtags = reply.draft().hashtags().stream().map(AiService::tag).collect(Collectors.joining(" "));
                d.platform = reply.draft().platform() == null ? "LINKEDIN" : reply.draft().platform();
                d.language = reply.draft().language() == null ? "en" : reply.draft().language();
                d.suggestedChannels = reply.draft().suggestedChannels().isEmpty() ? suggestedFor(d.platform, p.request().user().id())
                        : String.join(",", reply.draft().suggestedChannels());
                drafts.save(d);
                draft = draftDto(d);
            }
            conversations.findById(p.conversationId()).ifPresent(c -> {
                if (p.firstMessage()) c.title = p.text().length() > 60 ? p.text().substring(0, 60) : p.text();
                c.updatedAt = Instant.now();
            });
            return new Exchange(p.userMessage(), new MessageDto(bot.id, "ASSISTANT", bot.content, bot.createdAt, "COMPLETE", srcDtos, draft));
        });
    }

    /** A stopped or failed answer: whatever text had arrived, kept with its status. Nothing is saved when there is no text. */
    public UUID savePartial(PreparedChat p, String text, String status) {
        if (text == null || text.isBlank()) return null;
        return tx.execute(st -> {
            AiMessage bot = message(p.conversationId(), "ASSISTANT", text, Instant.now().plusMillis(1), status);
            conversations.findById(p.conversationId()).ifPresent(c -> c.updatedAt = Instant.now());
            return bot.id;
        });
    }

    @Transactional
    public FeedbackDto feedback(CurrentUser me, UUID messageId, String rating, String comment) {
        AiMessage m = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message not found"));
        owned(me, m.conversationId);
        if (!"ASSISTANT".equals(m.role)) throw ApiException.badRequest("validation", "Feedback is for assistant answers.");
        if (!"UP".equals(rating) && !"DOWN".equals(rating)) throw ApiException.badRequest("validation", "rating must be UP or DOWN");
        if (comment != null && comment.length() > 1000) throw ApiException.badRequest("validation", "comment is limited to 1000 characters");
        AiFeedback f = feedback.findByMessageIdAndUserId(messageId, me.employeeId()).orElseGet(AiFeedback::new);
        f.messageId = messageId;
        f.userId = me.employeeId();
        f.rating = rating;
        f.comment = comment == null || comment.isBlank() ? null : comment.trim();
        feedback.save(f);
        return new FeedbackDto(messageId, f.rating, f.comment);
    }

    public static final Map<String, Integer> CAPTION_LIMITS = Map.of("LINKEDIN", 3000, "INSTAGRAM", 2200, "FACEBOOK", 5000);
    private static final java.util.regex.Pattern HASHTAG = java.util.regex.Pattern.compile("^#?[\\p{L}\\p{N}_]{1,50}$");

    /** The owner of a draft edits headline, caption and hashtags before publishing. Platform limits apply. */
    @Transactional
    public DraftDto updateDraft(CurrentUser me, UUID draftId, String headline, String caption, List<String> hashtags) {
        AiDraft d = drafts.findById(draftId).orElseThrow(() -> ApiException.notFound("Draft not found"));
        AiMessage m = messages.findById(d.messageId).orElseThrow();
        owned(me, m.conversationId);
        if (!publishLog.findByDraftId(draftId).isEmpty())
            throw ApiException.conflict("draft-published", "This draft has already been published, so it can no longer be edited.");
        if (headline != null) {
            if (headline.isBlank() || headline.length() > 200) throw ApiException.badRequest("validation", "The headline needs 1 to 200 characters.");
            d.headline = headline.trim();
        }
        if (caption != null) {
            int limit = CAPTION_LIMITS.getOrDefault(d.platform, 3000);
            if (caption.isBlank()) throw ApiException.badRequest("validation", "The caption cannot be empty.");
            if (caption.length() > limit)
                throw ApiException.badRequest("caption-too-long", "A " + d.platform + " caption can have at most " + limit + " characters (now " + caption.length() + ").");
            d.caption = caption.trim();
        }
        if (hashtags != null) {
            if (hashtags.size() > 30) throw ApiException.badRequest("validation", "At most 30 hashtags.");
            for (String h : hashtags) if (h == null || !HASHTAG.matcher(h.trim()).matches())
                throw ApiException.badRequest("validation", "A hashtag has letters, digits or underscores only: " + h);
            d.hashtags = hashtags.stream().map(h -> tag(h.trim())).collect(Collectors.joining(" "));
        }
        return draftDto(d);
    }

    private String suggestedFor(String platform, UUID userId) {
        if (!"LINKEDIN".equals(platform)) return "";
        List<String> codes = new ArrayList<>(List.of("company-linkedin"));
        channels.findAllByOrderByIdAsc().stream().filter(c -> "LINKEDIN".equals(c.provider) && userId.equals(c.ownerId)).forEach(c -> codes.add(c.code));
        return String.join(",", codes);
    }

    private static String tag(String h) { return h.startsWith("#") ? h : "#" + h; }
    private static String trunc(String v, int max) { return v == null ? null : (v.length() > max ? v.substring(0, max) : v); }

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

    private AiMessage message(UUID conversationId, String role, String content, Instant at, String status) {
        AiMessage m = new AiMessage();
        m.id = UUID.randomUUID();
        m.conversationId = conversationId;
        m.role = role;
        m.content = content;
        m.createdAt = at;
        m.status = status;
        return messages.save(m);
    }

    private List<MessageDto> messageDtos(List<AiMessage> list) {
        List<UUID> ids = list.stream().map(m -> m.id).toList();
        Map<UUID, List<SourceDto>> src = ids.isEmpty() ? Map.of() : sources.findByMessageIdIn(ids).stream()
                .collect(Collectors.groupingBy(s -> s.messageId, Collectors.mapping(s -> new SourceDto(s.title, s.collection, s.page, s.sourceRef), Collectors.toList())));
        Map<UUID, DraftDto> dr = ids.isEmpty() ? Map.of() : drafts.findByMessageIdIn(ids).stream()
                .collect(Collectors.toMap(d -> d.messageId, this::draftDto));
        return list.stream().map(m -> new MessageDto(m.id, m.role, m.content, m.createdAt, m.status, src.getOrDefault(m.id, List.of()), dr.get(m.id))).toList();
    }

    private DraftDto draftDto(AiDraft d) {
        return new DraftDto(d.id, d.kind, d.headline, d.caption,
                d.hashtags.isBlank() ? List.of() : Arrays.asList(d.hashtags.split(" ")),
                d.suggestedChannels.isBlank() ? List.of() : Arrays.asList(d.suggestedChannels.split(",")), d.platform, d.language);
    }
}

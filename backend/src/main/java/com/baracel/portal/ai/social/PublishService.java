package com.baracel.portal.ai.social;

import com.baracel.portal.ai.domain.*;
import com.baracel.portal.ai.repo.*;
import com.baracel.portal.ai.social.LinkedInClient.Failure;
import com.baracel.portal.ai.social.LinkedInClient.LinkedInException;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.AuditService;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.common.web.PageResponse;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes a draft to channels. One result per (draft, channel): a repeated request returns the first result, so a double click posts once.
 * A definite failure (nothing was posted) may be tried again; an UNKNOWN result (timeout or unclear answer) is never retried automatically
 * and the person is told to check LinkedIn. The LinkedIn call runs outside any database transaction.
 */
@Service
public class PublishService {
    private static final Logger log = LoggerFactory.getLogger(PublishService.class);
    static final int LINKEDIN_MAX = 3000;
    private static final Pattern RESERVED = Pattern.compile("([|{}@\\[\\]()<>#\\\\*_~])");   // little text format, reserved characters

    public record PublishResult(String channel, String status, @Nullable String reason, @Nullable String message, @Nullable String postUrl) {}
    public record HistoryItem(Long id, Instant createdAt, String channel, String channelName, String headline, String status,
                              @Nullable String reason, @Nullable String message, @Nullable String postUrl) {}

    private final AiDraftRepository drafts;
    private final AiMessageRepository messages;
    private final AiConversationRepository conversations;
    private final SocialChannelRepository channels;
    private final PublishLogRepository log_;
    private final SocialConnectionService connections;
    private final ChannelService channelService;
    private final LinkedInClient linkedin;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public PublishService(AiDraftRepository drafts, AiMessageRepository messages, AiConversationRepository conversations,
                          SocialChannelRepository channels, PublishLogRepository publishLog, SocialConnectionService connections,
                          ChannelService channelService, LinkedInClient linkedin, AuditService audit, TransactionTemplate tx) {
        this.drafts = drafts;
        this.messages = messages;
        this.conversations = conversations;
        this.channels = channels;
        this.log_ = publishLog;
        this.connections = connections;
        this.channelService = channelService;
        this.linkedin = linkedin;
        this.audit = audit;
        this.tx = tx;
    }

    private record Prepared(AiDraft draft, Map<String, SocialChannel> channels) {}

    public List<PublishResult> publish(CurrentUser me, UUID draftId, List<String> channelCodes) {
        if (channelCodes == null || channelCodes.isEmpty()) throw ApiException.badRequest("validation", "Pick at least one channel.");
        Prepared p = tx.execute(st -> validate(me, draftId, channelCodes));
        List<PublishResult> out = new ArrayList<>();
        for (String code : new LinkedHashSet<>(channelCodes)) out.add(publishOne(me, p.draft(), p.channels().get(code)));
        return out;
    }

    /** Every channel is checked before anything is published, so a bad channel in the list publishes nothing. */
    private Prepared validate(CurrentUser me, UUID draftId, List<String> codes) {
        AiDraft d = drafts.findById(draftId).orElseThrow(() -> ApiException.notFound("Draft not found"));
        AiMessage m = messages.findById(d.messageId).orElseThrow();
        conversations.findById(m.conversationId).filter(c -> c.ownerId.equals(me.employeeId())).orElseThrow(() -> ApiException.notFound("Draft not found"));
        Map<String, SocialChannel> byCode = channels.findByCodeIn(codes).stream().collect(Collectors.toMap(c -> c.code, c -> c));
        for (String code : codes) {
            SocialChannel c = byCode.get(code);
            if (c == null) throw ApiException.badRequest("unknown-channel", "Unknown channel: " + code);
            if (!"LINKEDIN".equals(c.provider))
                throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "channel-not-available", c.name + " publishing is coming later.");
            boolean company = "COMPANY".equals(c.type);
            if (company ? !me.has("social:publish:company") : !(me.has("social:publish:personal") && me.employeeId().equals(c.ownerId)))
                throw ApiException.forbidden("You cannot publish to " + c.name + ".");
            ChannelService.ChannelDto dto = channelService.dto(me, c);
            if (ChannelService.NOT_AVAILABLE.equals(dto.status()))
                throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "channel-not-available", c.name + " publishing is not enabled yet.");
            if (ChannelService.NOT_CONNECTED.equals(dto.status())) throw ApiException.conflict("channel-not-connected", c.name + " is not connected.");
        }
        return new Prepared(d, byCode);
    }

    private PublishResult publishOne(CurrentUser me, AiDraft d, SocialChannel c) {
        Claim claim = tx.execute(st -> claim(me, d, c));
        if (!claim.mine()) return result(c.code, claim.row());   // the first result, whatever it was
        PublishLog row = claim.row();

        String status, reason = null, postId = null, postUrl = null;
        if (!connections.linkedinEnabled()) {
            status = "SIMULATED";   // switched off for local development: nothing is sent to LinkedIn
        } else {
            SocialConnection conn = connections.find("COMPANY".equals(c.type) ? null : c.ownerId, "LINKEDIN");
            try {
                if (conn == null) throw new SocialConnectionService.TokenExpired();
                String token = connections.accessToken(conn.id);
                String text = commentary(d.headline, d.caption, d.hashtags);
                if (text.length() > LINKEDIN_MAX) throw new LinkedInException(Failure.REJECTED, 0, "The post is longer than " + LINKEDIN_MAX + " characters.");
                String id = linkedin.createPost(token, conn.accountUrn, text).id();
                status = "PUBLISHED";
                postId = id;
                postUrl = "https://www.linkedin.com/feed/update/" + id + "/";
            } catch (SocialConnectionService.TokenExpired e) {
                status = "FAILED"; reason = "TOKEN_EXPIRED";
            } catch (LinkedInException e) {
                switch (e.failure()) {
                    case UNCLEAR -> status = "UNKNOWN";   // a timeout or an unclear answer: the post may exist. Never retried.
                    case TOKEN_EXPIRED -> { status = "FAILED"; reason = "TOKEN_EXPIRED"; connections.markExpired(conn == null ? null : conn.id); }
                    case RATE_LIMITED -> { status = "FAILED"; reason = "RATE_LIMITED"; }
                    case REJECTED -> { status = "FAILED"; reason = "REJECTED"; }
                    default -> { status = "FAILED"; reason = "UNKNOWN_ERROR"; }
                }
            } catch (RuntimeException e) {
                log.error("Publishing failed unexpectedly: {}", e.getClass().getSimpleName());
                status = "UNKNOWN";   // we cannot tell whether LinkedIn got it
            }
        }
        String fStatus = status, fReason = reason, fPostId = postId, fPostUrl = postUrl;
        PublishLog saved = tx.execute(st -> {
            PublishLog r = log_.findById(row.id).orElseThrow();
            r.status = fStatus;
            r.reason = fReason;
            r.postId = fPostId;
            r.postUrl = fPostUrl;
            r.updatedAt = Instant.now();
            log_.flush();
            audit.log(me, "AI_DRAFT_PUBLISH_" + fStatus, "ai-draft", d.id.toString(), "channel=" + c.code + (fReason == null ? "" : " reason=" + fReason));
            return r;
        });
        return result(c.code, saved);
    }

    private record Claim(boolean mine, PublishLog row) {}

    /** Inserts the IN_PROGRESS row. The unique (draft, channel) key decides who publishes when two requests race. */
    private Claim claim(CurrentUser me, AiDraft d, SocialChannel c) {
        Optional<PublishLog> existing = log_.findByDraftIdAndChannelCode(d.id, c.code);
        if (existing.isPresent()) {
            PublishLog e = existing.get();
            if ("FAILED".equals(e.status) && log_.claimFailed(e.id) == 1) return new Claim(true, log_.findById(e.id).orElseThrow());   // nothing was posted: try again
            return new Claim(false, e);
        }
        PublishLog r = new PublishLog();
        r.draftId = d.id;
        r.channelCode = c.code;
        r.userId = me.employeeId();
        r.userName = me.name();
        r.status = "IN_PROGRESS";
        try {
            return new Claim(true, log_.saveAndFlush(r));
        } catch (DataIntegrityViolationException e) {
            return new Claim(false, log_.findByDraftIdAndChannelCode(d.id, c.code).orElseThrow());
        }
    }

    private PublishResult result(String channel, PublishLog r) {
        String status = "IN_PROGRESS".equals(r.status) ? "UNKNOWN" : r.status;
        String message = switch (status) {
            case "UNKNOWN" -> "LinkedIn did not confirm the post. Check LinkedIn before you publish again; it will not be retried automatically.";
            case "SIMULATED" -> "Simulated: nothing was posted.";
            case "FAILED" -> switch (r.reason == null ? "" : r.reason) {
                case "TOKEN_EXPIRED" -> "Your LinkedIn connection has expired. Reconnect it, then publish again.";
                case "RATE_LIMITED" -> "LinkedIn is limiting posts right now. Try again later.";
                case "REJECTED" -> "LinkedIn rejected the post.";
                default -> "LinkedIn could not publish the post.";
            };
            default -> null;
        };
        return new PublishResult(channel, status, r.reason, message, r.postUrl);
    }

    /** headline, caption and hashtags as LinkedIn commentary. Reserved characters are escaped; hashtags are written as #words. */
    static String commentary(String headline, String caption, String hashtags) {
        StringBuilder sb = new StringBuilder();
        if (headline != null && !headline.isBlank()) sb.append(escape(headline.trim())).append("\n\n");
        sb.append(escape(caption == null ? "" : caption.trim()));
        String tags = Arrays.stream((hashtags == null ? "" : hashtags).split("\\s+")).map(t -> t.replaceAll("[^\\p{L}\\p{N}]", ""))
                .filter(t -> !t.isEmpty()).map(t -> "#" + t).collect(Collectors.joining(" "));
        if (!tags.isEmpty()) sb.append("\n\n").append(tags);
        return sb.toString();
    }

    static String escape(String s) { return RESERVED.matcher(s).replaceAll("\\\\$1"); }

    // ---- history ----

    public PageResponse<HistoryItem> history(CurrentUser me, int page, int size) {
        return tx.execute(st -> {
            var rows = log_.findByUserIdOrderByCreatedAtDesc(me.employeeId(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
            Map<UUID, AiDraft> byId = drafts.findAllById(rows.stream().map(r -> r.draftId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(x -> x.id, x -> x));
            Map<String, String> names = channels.findAllByOrderByIdAsc().stream().collect(Collectors.toMap(c -> c.code, c -> c.name, (a, b) -> a));
            return PageResponse.of(rows, r -> {
                PublishResult pr = result(r.channelCode, r);
                AiDraft d = byId.get(r.draftId);
                return new HistoryItem(r.id, r.createdAt, r.channelCode, names.getOrDefault(r.channelCode, r.channelCode), d == null ? "" : d.headline,
                        pr.status(), pr.reason(), pr.message(), pr.postUrl());
            });
        });
    }
}

package com.baracel.portal.ai.service;

import com.baracel.portal.ai.service.AiAssistantPort.*;
import com.baracel.portal.ai.service.AiService.*;
import com.baracel.portal.ai.service.StreamingAiAssistant.AiEvent;
import com.baracel.portal.common.security.CurrentUser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Relays the AI service stream to the browser as server-sent events (token, sources, draft, done, error).
 * When the final event arrives the answer, its sources and its draft are saved. If the browser goes away first, the upstream call
 * is cancelled and the text received so far is saved with status STOPPED.
 */
@Service
public class AiChatStreamService {
    private static final Logger log = LoggerFactory.getLogger(AiChatStreamService.class);
    static final long EMITTER_TIMEOUT_MS = 5 * 60_000L;

    private final AiService ai;
    private final AiAssistantPort assistant;
    private final ChatRateLimiter rateLimiter;

    public AiChatStreamService(AiService ai, AiAssistantPort assistant, ChatRateLimiter rateLimiter) {
        this.ai = ai;
        this.assistant = assistant;
        this.rateLimiter = rateLimiter;
    }

    public SseEmitter stream(CurrentUser me, UUID conversationId, String text) {
        rateLimiter.check(me.employeeId());
        StreamingAiAssistant streaming = assistant instanceof StreamingAiAssistant s ? s : null;
        if (streaming != null) streaming.preflight();   // breaker open: plain 503 ai-unavailable before the stream starts
        PreparedChat prepared = ai.prepare(me, conversationId, text);
        Flux<AiEvent> upstream = streaming != null ? streaming.stream(prepared.request()) : simulated(prepared.request());

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Relay relay = new Relay(emitter, prepared, streaming);
        emitter.onCompletion(relay::clientGone);
        emitter.onTimeout(relay::clientGone);
        emitter.onError(t -> relay.clientGone());
        relay.subscription = upstream.subscribe(relay::onEvent, relay::onError, relay::onComplete);
        return emitter;
    }

    /** The Phase 1 stub cannot stream: answer in one go and emit the same events. */
    private Flux<AiEvent> simulated(AiRequest req) {
        return Flux.defer(() -> {
            AiReply r = assistant.chat(req);
            List<AiEvent> events = new ArrayList<>();
            for (String word : r.text().split("(?<=\\s)")) events.add(AiEvent.token(word));
            events.add(AiEvent.sources(r.sources()));
            if (r.draft() != null) events.add(AiEvent.draft(r.draft()));
            events.add(AiEvent.done());
            return Flux.fromIterable(events);
        });
    }

    private final class Relay {
        final SseEmitter emitter;
        final PreparedChat prepared;
        final StreamingAiAssistant streaming;
        final StringBuilder text = new StringBuilder();
        final AtomicBoolean finished = new AtomicBoolean();
        volatile Disposable subscription;
        List<AiSource> sources = List.of();
        AiDraftReply draft;

        Relay(SseEmitter emitter, PreparedChat prepared, StreamingAiAssistant streaming) {
            this.emitter = emitter;
            this.prepared = prepared;
            this.streaming = streaming;
        }

        synchronized void onEvent(AiEvent ev) {
            if (finished.get()) return;
            try {
                switch (ev.type()) {
                    case "token" -> {
                        text.append(ev.text());
                        send("token", Map.of("text", ev.text()));
                    }
                    case "sources" -> {
                        sources = ev.sources();
                        send("sources", Map.of("sources", ev.sources()));
                    }
                    case "draft" -> draft = ev.draft();   // sent after it is saved, so the browser gets its id
                    case "done" -> finish();
                    case "error" -> {
                        if (finished.compareAndSet(false, true)) {
                            ai.savePartial(prepared, text.toString(), "FAILED");
                            report(true);
                            send("error", Map.of("code", ev.code() == null ? "error" : ev.code(), "detail", ev.detail() == null ? "" : ev.detail()));
                            emitter.complete();
                        }
                    }
                    default -> { }
                }
            } catch (IOException e) {
                clientGone();   // the browser disconnected while we were sending
            } catch (RuntimeException e) {
                log.error("Chat stream failed while handling {}", ev.type(), e);
                fail("internal-error", "Something went wrong.");
            }
        }

        void finish() throws IOException {
            if (!finished.compareAndSet(false, true)) return;
            Exchange ex = ai.complete(prepared, new AiReply(text.toString(), sources, draft));
            report(true);
            if (ex.assistantMessage().draft() != null) send("draft", Map.of("draft", ex.assistantMessage().draft()));
            send("done", Map.of("conversationId", prepared.conversationId(), "userMessage", ex.userMessage(), "assistantMessage", ex.assistantMessage()));
            emitter.complete();
        }

        synchronized void onError(Throwable t) {
            if (finished.get()) return;
            boolean unavailable = com.baracel.portal.ai.client.AiServiceClient.isUnavailable(t);
            log.warn("AI stream failed: {}", t.getClass().getSimpleName());
            if (unavailable) report(false);
            fail(unavailable ? "ai-unavailable" : "error", unavailable ? "The AI service is not available right now. Try again in a minute." : "The answer could not be completed.");
        }

        synchronized void onComplete() {
            if (finished.get()) return;
            // the stream ended without a done event
            if (finished.compareAndSet(false, true)) {
                ai.savePartial(prepared, text.toString(), "FAILED");
                try { send("error", Map.of("code", "stream-ended", "detail", "The answer ended early.")); } catch (IOException ignored) { /* gone */ }
                emitter.complete();
            }
        }

        void fail(String code, String detail) {
            if (!finished.compareAndSet(false, true)) return;
            ai.savePartial(prepared, text.toString(), "FAILED");
            try { send("error", Map.of("code", code, "detail", detail)); } catch (IOException ignored) { /* gone */ }
            emitter.complete();
        }

        /** The browser left: cancel the upstream call and keep what we have, marked STOPPED. */
        synchronized void clientGone() {
            if (!finished.compareAndSet(false, true)) return;
            Disposable d = subscription;
            if (d != null) d.dispose();
            ai.savePartial(prepared, text.toString(), "STOPPED");
            log.info("Chat stream stopped by the client, {} characters saved", text.length());
        }

        void report(boolean ok) { if (streaming != null) streaming.outcome(ok); }

        void send(String name, Object data) throws IOException {
            emitter.send(SseEmitter.event().name(name).data(data, org.springframework.http.MediaType.APPLICATION_JSON));
        }
    }
}

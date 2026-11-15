package com.baracel.portal.ai.service;

import com.baracel.portal.ai.service.AiAssistantPort.*;
import java.util.List;
import reactor.core.publisher.Flux;

/** An assistant that can stream its answer (the HTTP client does; the Phase 1 stub does not). */
public interface StreamingAiAssistant {
    /** One server-sent event of the AI service: token, sources, draft, done or error. */
    record AiEvent(String type, String text, List<AiSource> sources, AiDraftReply draft, String code, String detail) {
        public static AiEvent token(String text) { return new AiEvent("token", text, null, null, null, null); }
        public static AiEvent sources(List<AiSource> s) { return new AiEvent("sources", null, s, null, null, null); }
        public static AiEvent draft(AiDraftReply d) { return new AiEvent("draft", null, null, d, null, null); }
        public static AiEvent done() { return new AiEvent("done", null, null, null, null, null); }
        public static AiEvent error(String code, String detail) { return new AiEvent("error", null, null, null, code, detail); }
    }

    Flux<AiEvent> stream(AiRequest request);

    /** Called before a stream starts: throws 503 ai-unavailable when the circuit breaker is open. */
    default void preflight() {}

    /** Reports how a stream ended so the circuit breaker can count it: true = the service answered, false = it failed. */
    default void outcome(boolean ok) {}
}

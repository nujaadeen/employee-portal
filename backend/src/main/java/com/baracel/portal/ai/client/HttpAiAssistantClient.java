package com.baracel.portal.ai.client;

import com.baracel.portal.ai.service.AiAssistantPort;
import com.baracel.portal.ai.service.StreamingAiAssistant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** AiAssistantPort for ai.mode=http: talks to the FastAPI service. Sends allowed_collections, history and the user on every call. */
public class HttpAiAssistantClient implements AiAssistantPort, StreamingAiAssistant {
    private static final Logger log = LoggerFactory.getLogger(HttpAiAssistantClient.class);

    private final AiServiceClient client;
    private final ObjectMapper mapper;

    public HttpAiAssistantClient(AiServiceClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    static Map<String, Object> body(AiRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conversation_id", r.conversationId().toString());
        m.put("message", r.userMessage());
        List<Map<String, String>> history = new ArrayList<>();
        for (AiTurn t : r.history()) history.add(Map.of("role", t.role().toLowerCase(), "content", t.content()));
        m.put("history", history);
        m.put("user", Map.of("id", r.user().id().toString(), "name", r.user().name(), "roles", r.user().roles()));
        m.put("allowed_collections", r.allowedCollections());
        m.put("mode", "auto");
        return m;
    }

    @Override
    public AiReply chat(AiRequest request) {
        JsonNode n = client.chat(body(request));
        List<AiSource> sources = AiServiceClient.sources(n.path("sources"));
        AiDraftReply draft = AiServiceClient.draft(n.path("draft"));
        if (draft != null && n.path("draft").has("sources")) sources = AiServiceClient.sources(n.path("draft").path("sources"));
        return new AiReply(n.path("text").asString(""), sources, draft);
    }

    @Override
    public void preflight() { client.acquire(); }

    @Override
    public void outcome(boolean ok) { if (ok) client.breaker().success(); else client.breaker().failure(); }

    @Override
    public Flux<AiEvent> stream(AiRequest request) {
        return client.stream(body(request)).concatMap(sse -> {
            String type = sse.event() == null ? "message" : sse.event();
            String data = sse.data() == null ? "{}" : sse.data();
            try {
                JsonNode n = mapper.readTree(data);
                return switch (type) {
                    case "token" -> Flux.just(AiEvent.token(n.path("text").asString("")));
                    case "sources" -> Flux.just(AiEvent.sources(AiServiceClient.sources(n.path("sources"))));
                    case "draft" -> Flux.just(AiEvent.draft(AiServiceClient.draft(n.path("draft"))));
                    case "done" -> Flux.just(AiEvent.done());
                    case "error" -> Flux.just(AiEvent.error(n.path("code").asString("error"), n.path("detail").asString("")));
                    default -> Flux.<AiEvent>empty();
                };
            } catch (RuntimeException e) {
                log.warn("Unreadable event from the AI service: {}", type);
                return Flux.<AiEvent>empty();
            }
        });
    }
}

package com.baracel.portal.ai.service;

import java.util.List;
import java.util.UUID;

/**
 * The only way Spring talks to an assistant. Phase 1 has one implementation, {@link StubAiAssistant}.
 * Phase 2 adds an HTTP implementation for the FastAPI service. The assistant never decides access:
 * {@code allowedCollections} is computed by Spring from the caller's permissions.
 */
public interface AiAssistantPort {
    record AiUser(UUID id, String name, List<String> roles) {}
    record AiTurn(String role, String content) {}
    record AiRequest(UUID conversationId, String userMessage, List<AiTurn> history, AiUser user, List<String> allowedCollections) {}
    record AiSource(String title, String collection, Integer page, String sourceRef) {
        public AiSource(String title, String collection) { this(title, collection, null, null); }
    }
    record AiDraftReply(String kind, String headline, String caption, List<String> hashtags, List<String> suggestedChannels,
                        String platform, String language) {
        public AiDraftReply(String kind, String headline, String caption, List<String> hashtags, List<String> suggestedChannels) {
            this(kind, headline, caption, hashtags, suggestedChannels, "LINKEDIN", "en");
        }
    }
    record AiReply(String text, List<AiSource> sources, AiDraftReply draft) {}

    AiReply chat(AiRequest request);
}

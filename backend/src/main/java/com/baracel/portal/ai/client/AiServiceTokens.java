package com.baracel.portal.ai.client;

import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Service tokens for the AI service: Spring's OAuth2 client with the client credentials grant for the Keycloak client
 * portal-backend-ai. Two registrations (same client, different scope) so a chat call never carries ai:ingest.
 * Spring caches the token and fetches a new one shortly before it expires.
 */
public class AiServiceTokens {
    public static final String INVOKE = "ai:invoke";
    public static final String INGEST = "ai:ingest";

    private final AuthorizedClientServiceOAuth2AuthorizedClientManager manager;

    public AiServiceTokens(AiProperties.Service cfg) {
        InMemoryClientRegistrationRepository repo = new InMemoryClientRegistrationRepository(
                registration("ai-invoke", INVOKE, cfg), registration("ai-ingest", INGEST, cfg));
        this.manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(repo, new InMemoryOAuth2AuthorizedClientService(repo));
    }

    private static ClientRegistration registration(String id, String scope, AiProperties.Service cfg) {
        return ClientRegistration.withRegistrationId(id).clientId(cfg.clientId()).clientSecret(cfg.clientSecret())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri(cfg.tokenUri()).scope(scope).build();
    }

    /** A valid access token carrying exactly the given scope (ai:invoke or ai:ingest). */
    public String token(String scope) {
        String id = INGEST.equals(scope) ? "ai-ingest" : "ai-invoke";
        OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(id).principal("portal-backend").build());
        if (client == null) throw new IllegalStateException("No token for the AI service");
        return client.getAccessToken().getTokenValue();
    }
}

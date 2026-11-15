package com.baracel.portal.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * The application in ai.mode=http with LinkedIn switched on, talking to WireMock instead of the AI service, Keycloak and LinkedIn.
 * One WireMock server plays all three (paths do not overlap): /token (Keycloak), /v1/... (AI service), /oauth, /v2, /rest (LinkedIn).
 */
@TestPropertySource(properties = {
        "portal.ai.mode=http",
        "portal.ai.service.client-secret=test-secret",
        "portal.ai.service.chat-timeout=1500ms",
        "portal.ai.service.stream-idle-timeout=5s",
        "portal.ai.service.max-upload-mb=1",
        "portal.ai.breaker.failure-threshold=3",
        "social.linkedin.enabled=true",
        "social.linkedin.company-enabled=true",
        "social.linkedin.client-id=li-client",
        "social.linkedin.client-secret=li-secret",
        "social.linkedin.read-timeout=1s",
        "social.frontend-url=http://frontend.test",
        "social.crypto.token-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",   // 32 bytes, test only
        "social.crypto.token-key-id=k1"
})
public abstract class HttpModeIT extends IntegrationTest {
    protected static final WireMockServer WM = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static { WM.start(); }

    @DynamicPropertySource
    static void wiremock(DynamicPropertyRegistry r) {
        String base = "http://localhost:" + WM.port();
        r.add("portal.ai.service.url", () -> base);
        r.add("portal.ai.service.token-uri", () -> base + "/token");
        r.add("social.linkedin.auth-base", () -> base);
        r.add("social.linkedin.api-base", () -> base);
    }

    @BeforeEach
    void resetWireMock() {
        WM.resetAll();
        WireMock.configureFor("localhost", WM.port());
        tokenEndpoint();
    }

    @AfterEach
    void afterEachWireMock() { WM.resetAll(); }

    /** Keycloak: client credentials. The token value tells which scope was asked for. */
    protected void tokenEndpoint() {
        for (String scope : new String[]{"ai:invoke", "ai:ingest"}) {
            WM.stubFor(WireMock.post("/token").withRequestBody(WireMock.containing(java.net.URLEncoder.encode(scope, java.nio.charset.StandardCharsets.UTF_8)))
                    .willReturn(WireMock.okJson("{\"access_token\":\"svc-" + scope.substring(3) + "-token\",\"token_type\":\"Bearer\",\"expires_in\":300,\"scope\":\"" + scope + "\"}")));
        }
    }
}

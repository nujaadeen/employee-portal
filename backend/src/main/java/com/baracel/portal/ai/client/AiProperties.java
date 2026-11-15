package com.baracel.portal.ai.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** portal.ai.*: mode stub or http, the AI service address and its Keycloak client. */
@ConfigurationProperties("portal.ai")
public record AiProperties(
        @DefaultValue("stub") String mode,
        @DefaultValue Service service,
        @DefaultValue Breaker breaker,
        @DefaultValue RateLimit rateLimit) {

    public record Service(
            @DefaultValue("http://localhost:8000") String url,
            @DefaultValue("http://localhost:8180/realms/baracel/protocol/openid-connect/token") String tokenUri,
            @DefaultValue("portal-backend-ai") String clientId,
            @DefaultValue("") String clientSecret,
            @DefaultValue("3s") Duration connectTimeout,
            @DefaultValue("90s") Duration chatTimeout,
            @DefaultValue("120s") Duration streamIdleTimeout,
            @DefaultValue("15s") Duration adminTimeout,
            @DefaultValue("25") int maxUploadMb) {}

    /** Opens after this many failures in a row, stays open for openFor, then lets one call through. */
    public record Breaker(@DefaultValue("5") int failureThreshold, @DefaultValue("30s") Duration openFor) {}

    public record RateLimit(@DefaultValue("20") int chatPerMinute) {}
}

package com.baracel.portal.support;

import com.baracel.portal.access.service.KeycloakAdmin;
import com.baracel.portal.gitea.service.GiteaClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {
    /** The demo seed is written for Friday 13 November 2026. */
    public static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Bean @Primary JwtDecoder testJwtDecoder() { return new TestJwtDecoder(); }
    @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    @Bean @Primary KeycloakAdmin fakeKeycloakAdmin() { return new FakeKeycloakAdmin(); }
    @Bean @Primary GiteaClient fakeGiteaClient() { return new FakeGiteaClient(); }
}

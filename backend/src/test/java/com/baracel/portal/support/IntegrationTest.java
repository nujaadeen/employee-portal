package com.baracel.portal.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.baracel.portal.access.service.AccessService;
import com.baracel.portal.access.service.KeycloakAdmin;
import com.baracel.portal.gitea.service.GiteaClient;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL (Testcontainers), real Flyway migrations plus the demo seed, real security chain. Each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@Transactional
public abstract class IntegrationTest {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected AccessService accessService;
    @Autowired protected KeycloakAdmin keycloak;
    @Autowired protected GiteaClient gitea;

    @AfterEach
    void resetShared() {
        accessService.clearCache();
        ((FakeKeycloakAdmin) keycloak).reset();
        ((FakeGiteaClient) gitea).reset();
    }

    protected static String as(String username) { return "Bearer user~" + username; }

    /** A sign-in that has no employee row yet. */
    protected static String custom(String sub, String email, String first, String last) {
        String raw = sub + ":" + email + ":" + first + ":" + last;
        return "Bearer custom~" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected MockHttpServletRequestBuilder getAs(String user, String url) { return get(url).header("Authorization", as(user)); }

    protected MockHttpServletRequestBuilder postAs(String user, String url, String json) {
        return post(url).header("Authorization", as(user)).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected MockHttpServletRequestBuilder putAs(String user, String url, String json) {
        return put(url).header("Authorization", as(user)).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected MockHttpServletRequestBuilder patchAs(String user, String url, String json) {
        return patch(url).header("Authorization", as(user)).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected MockHttpServletRequestBuilder deleteAs(String user, String url) { return delete(url).header("Authorization", as(user)); }
}

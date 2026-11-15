package com.baracel.portal.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.ai.client.SimpleCircuitBreaker;
import com.baracel.portal.support.HttpModeIT;
import com.baracel.portal.support.TestUsers;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** ai.mode=http against WireMock: what Spring sends, how it fails, and how it limits. */
class AiHttpIT extends HttpModeIT {
    static final String ANSWER = """
            {"conversation_id":"c","intent":"answer","language":"en","text":"Hold the button for five seconds [1]",
             "sources":[{"index":1,"title":"Lantern Pro user guide.pdf","collection":"product","page":2,"source_ref":"guide.pdf","score":0.67}],"draft":null}""";

    @Autowired JdbcTemplate jdbc;
    @Autowired SimpleCircuitBreaker breaker;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

    @BeforeEach
    void closeBreaker() { breaker.success(); }

    String conversation(String user) throws Exception {
        return JsonPath.read(mvc.perform(postAs(user, "/api/ai/conversations", "{}")).andReturn().getResponse().getContentAsString(), "$.id");
    }

    String send(String user, String conv, String text) throws Exception {
        return mvc.perform(postAs(user, "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"" + text + "\"}")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void sendsAllowedCollectionsHistoryAndTheUserWithAnInvokeToken() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(okJson(ANSWER)));
        String conv = conversation("aisha.rahman");
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"How do I pair it?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.content").value("Hold the button for five seconds [1]"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].collection").value("product"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].page").value(2))
                .andExpect(jsonPath("$.assistantMessage.status").value("COMPLETE"));
        WM.verify(1, postRequestedFor(urlEqualTo("/v1/chat"))
                .withHeader("Authorization", equalTo("Bearer svc-invoke-token"))   // the chat call carries ai:invoke only
                .withRequestBody(matchingJsonPath("$.allowed_collections", equalToJson("[\"product\",\"brand\",\"campaign\"]")))
                .withRequestBody(matchingJsonPath("$.user.id", equalTo(TestUsers.id("aisha.rahman").toString())))
                .withRequestBody(matchingJsonPath("$.user.name", equalTo("Aisha Rahman")))
                .withRequestBody(matchingJsonPath("$.user.roles", containing("SALES")))
                .withRequestBody(matchingJsonPath("$.message", equalTo("How do I pair it?"))));
        // Lena (HR) may use hr-policies only
        String lena = conversation("lena.fischer");
        send("lena.fischer", lena, "What is the leave policy?");
        WM.verify(1, postRequestedFor(urlEqualTo("/v1/chat")).withRequestBody(matchingJsonPath("$.allowed_collections", equalToJson("[\"hr-policies\"]"))));
        // Priya (ENGINEER) engineering only; Sara (EMPLOYEE) none: an empty list, never "everything"
        send("priya.nair", conversation("priya.nair"), "How do deployments work?");
        WM.verify(1, postRequestedFor(urlEqualTo("/v1/chat")).withRequestBody(matchingJsonPath("$.allowed_collections", equalToJson("[\"engineering\"]"))));
        send("sara.qureshi", conversation("sara.qureshi"), "Anything?");
        WM.verify(1, postRequestedFor(urlEqualTo("/v1/chat")).withRequestBody(matchingJsonPath("$.allowed_collections", equalToJson("[]"))));
    }

    @Test
    void sendsOnlyTheLastTenMessagesAsHistory() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(okJson(ANSWER)));
        String conv = conversation("aisha.rahman");
        em.flush();   // the conversation is saved in this test's transaction; flush it before inserting rows that point at it
        for (int i = 0; i < 12; i++)
            jdbc.update("insert into ai_message (id, conversation_id, role, content, created_at) values (gen_random_uuid(), ?::uuid, ?, ?, now() - ?::interval)",
                    conv, i % 2 == 0 ? "USER" : "ASSISTANT", "old " + i, (100 - i) + " minutes");
        send("aisha.rahman", conv, "latest question");
        WM.verify(postRequestedFor(urlEqualTo("/v1/chat"))
                .withRequestBody(matchingJsonPath("$.history.length()", equalTo("10")))
                .withRequestBody(matchingJsonPath("$.history[0].content", equalTo("old 2")))
                .withRequestBody(matchingJsonPath("$.history[9].content", equalTo("old 11")))
                .withRequestBody(matchingJsonPath("$.history[0].role", equalTo("user"))));
    }

    @Test
    void anUnavailableServiceIsAClear503AndIsNeverRetried() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(serverError()));
        String conv = conversation("aisha.rahman");
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", endsWith("/ai-unavailable")));
        WM.verify(1, postRequestedFor(urlEqualTo("/v1/chat")));   // no automatic retry
        WM.stubFor(post("/v1/chat").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"hi\"}")).andExpect(status().isServiceUnavailable());
        WM.verify(2, postRequestedFor(urlEqualTo("/v1/chat")));
    }

    @Test
    void aSlowServiceTimesOutAsAiUnavailable() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(okJson(ANSWER).withFixedDelay(4000)));   // chat-timeout is 1.5 s in tests
        String conv = conversation("aisha.rahman");
        long t0 = System.currentTimeMillis();
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.type", endsWith("/ai-unavailable")));
        org.junit.jupiter.api.Assertions.assertTrue(System.currentTimeMillis() - t0 < 3500, "gave up at the timeout, not at the server's pace");
    }

    @Test
    void theCircuitBreakerOpensAfterRepeatedFailuresAndStopsCalling() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(serverError()));
        String conv = conversation("aisha.rahman");
        for (int i = 0; i < 3; i++) send("aisha.rahman", conv, "hi");   // threshold is 3 in tests
        WM.verify(3, postRequestedFor(urlEqualTo("/v1/chat")));
        org.junit.jupiter.api.Assertions.assertEquals(SimpleCircuitBreaker.State.OPEN, breaker.state());
        for (int i = 0; i < 4; i++)
            mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"hi\"}"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.type", endsWith("/ai-unavailable")));
        WM.verify(3, postRequestedFor(urlEqualTo("/v1/chat")));   // the open breaker answered, the service was not called
        // knowledge calls share the breaker
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/jobs/abc")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void chatIsLimitedTo20MessagesPerMinutePerUser() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(okJson(ANSWER)));
        String conv = conversation("tom.becker");
        for (int i = 0; i < 20; i++)
            mvc.perform(postAs("tom.becker", "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"q" + i + "\"}")).andExpect(status().isOk());
        mvc.perform(postAs("tom.becker", "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"one too many\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")))
                .andExpect(jsonPath("$.type", endsWith("/rate-limited")));
        WM.verify(20, postRequestedFor(urlEqualTo("/v1/chat")));
        // another person is not affected
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + conversation("aisha.rahman") + "/messages", "{\"message\":\"hi\"}")).andExpect(status().isOk());
    }

    @Test
    void aDraftFromTheServiceIsSavedWithItsPlatformAndSuggestedChannels() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(okJson("""
                {"intent":"social_post","language":"en","text":"Here is a draft for Linkedin.","sources":[],
                 "draft":{"headline":"Light up the evenings","caption":"A short update.","hashtags":["Baracel","Autumn"],"platform":"LINKEDIN","language":"en",
                          "sources":[{"index":1,"title":"Autumn campaign brief.pdf","collection":"campaign","page":1,"source_ref":"brief.pdf","score":0.8}]}}""")));
        String conv = conversation("aisha.rahman");
        String body = send("aisha.rahman", conv, "Write a LinkedIn post");
        org.junit.jupiter.api.Assertions.assertEquals("Light up the evenings", JsonPath.read(body, "$.assistantMessage.draft.headline"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("#Baracel", "#Autumn"), JsonPath.read(body, "$.assistantMessage.draft.hashtags"));
        org.junit.jupiter.api.Assertions.assertEquals("LINKEDIN", JsonPath.read(body, "$.assistantMessage.draft.platform"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("company-linkedin", "personal-linkedin-aisha"), JsonPath.read(body, "$.assistantMessage.draft.suggestedChannels"));
        org.junit.jupiter.api.Assertions.assertEquals("campaign", JsonPath.read(body, "$.assistantMessage.sources[0].collection"));
    }
}

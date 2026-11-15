package com.baracel.portal.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.baracel.portal.ai.client.SimpleCircuitBreaker;
import com.baracel.portal.support.HttpModeIT;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streaming chat: the relay runs on other threads and saves in its own transactions, so these tests commit
 * (they use Aisha's seeded conversation and delete what they added).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiStreamIT extends HttpModeIT {
    static final String CONVERSATION = "d0000000-0000-4000-8000-000000000002";
    static final String SOURCES = "{\"sources\":[{\"index\":1,\"title\":\"Lantern Pro user guide.pdf\",\"collection\":\"product\",\"page\":2,\"source_ref\":\"guide.pdf\",\"score\":0.67}]}";

    @Autowired JdbcTemplate jdbc;
    @Autowired SimpleCircuitBreaker breaker;
    Instant started;

    @BeforeEach
    void mark() {
        started = Instant.now().minusSeconds(1);
        breaker.success();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from ai_message where conversation_id = ?::uuid and created_at > ?", CONVERSATION, java.sql.Timestamp.from(started));
    }

    static String sse(String... events) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < events.length; i += 2) sb.append("event: ").append(events[i]).append("\ndata: ").append(events[i + 1]).append("\n\n");
        return sb.toString();
    }

    MvcResult stream(String user, String text) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/conversations/" + CONVERSATION + "/messages").header("Authorization", as(user))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content("{\"message\":\"" + text + "\"}"))
                .andExpect(request().asyncStarted()).andReturn();
    }

    static String waitFor(MvcResult r, String needle) throws Exception {
        long end = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < end) {
            String body = r.getResponse().getContentAsString();
            if (body.contains(needle)) return body;
            Thread.sleep(50);
        }
        fail("Timed out waiting for " + needle + ". Got: " + r.getResponse().getContentAsString());
        return null;
    }

    List<Map<String, Object>> assistantMessages() {
        return jdbc.queryForList("select id, content, status from ai_message where conversation_id = ?::uuid and role = 'ASSISTANT' and created_at > ? order by created_at",
                CONVERSATION, java.sql.Timestamp.from(started));
    }

    @Test
    void relaysTheEventsInOrderAndSavesTheAnswerWithSourcesWhenDoneArrives() throws Exception {
        WM.stubFor(post("/v1/chat").withHeader("Accept", containing("text/event-stream")).willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody(sse("token", "{\"text\":\"Hold \"}", "token", "{\"text\":\"the button [1]\"}", "sources", SOURCES, "done", "{\"intent\":\"answer\"}"))));
        MvcResult r = stream("aisha.rahman", "How do I pair it?");
        String body = waitFor(r, "event:done");
        assertTrue(body.indexOf("event:token") < body.indexOf("event:sources") && body.indexOf("event:sources") < body.indexOf("event:done"), body);
        assertTrue(body.contains("Hold ") && body.contains("the button [1]"));
        assertFalse(body.contains("event:draft"));
        // saved: the answer, its source, and the question
        List<Map<String, Object>> saved = assistantMessages();
        assertEquals(1, saved.size());
        assertEquals("Hold the button [1]", saved.get(0).get("content"));
        assertEquals("COMPLETE", saved.get(0).get("status"));
        assertEquals(1, jdbc.queryForObject("select count(*) from ai_message_source where message_id = ?::uuid and collection = 'product' and page = 2", Integer.class, saved.get(0).get("id").toString()));
        assertEquals(1, jdbc.queryForObject("select count(*) from ai_message where conversation_id = ?::uuid and role = 'USER' and content = 'How do I pair it?'", Integer.class, CONVERSATION));
        assertTrue(body.contains(saved.get(0).get("id").toString()), "done carries the saved message id");
        WM.verify(postRequestedFor(urlEqualTo("/v1/chat")).withHeader("Authorization", equalTo("Bearer svc-invoke-token")));
    }

    @Test
    void aDraftIsSavedAndSentWithItsIdBeforeDone() throws Exception {
        String draft = "{\"draft\":{\"headline\":\"Light up the evenings\",\"caption\":\"A short update.\",\"hashtags\":[\"Baracel\"],\"platform\":\"LINKEDIN\",\"language\":\"en\",\"sources\":[]}}";
        WM.stubFor(post("/v1/chat").willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
                .withBody(sse("token", "{\"text\":\"Here is a draft.\"}", "sources", SOURCES, "draft", draft, "done", "{}"))));
        String body = waitFor(stream("aisha.rahman", "Write a LinkedIn post"), "event:done");
        assertTrue(body.indexOf("event:sources") < body.indexOf("event:draft") && body.indexOf("event:draft") < body.indexOf("event:done"), body);
        assertEquals(1, jdbc.queryForObject("select count(*) from ai_draft d join ai_message m on m.id = d.message_id where m.conversation_id = ?::uuid and d.headline = 'Light up the evenings' and d.platform = 'LINKEDIN'", Integer.class, CONVERSATION));
        String draftId = jdbc.queryForObject("select d.id::text from ai_draft d join ai_message m on m.id = d.message_id where m.conversation_id = ?::uuid and d.headline = 'Light up the evenings'", String.class, CONVERSATION);
        assertTrue(body.contains(draftId));
    }

    @Test
    void whenTheBrowserLeavesTheUpstreamCallIsCancelledAndThePartialTextIsSavedAsStopped() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withChunkedDribbleDelay(8, 4000)
                .withBody(sse("token", "{\"text\":\"Partial \"}", "token", "{\"text\":\"answer \"}", "token", "{\"text\":\"that never \"}", "token", "{\"text\":\"finishes\"}",
                        "sources", SOURCES, "done", "{}"))));
        MvcResult r = stream("aisha.rahman", "Tell me a long story");
        waitFor(r, "Partial");
        r.getRequest().getAsyncContext().complete();   // the browser went away
        long end = System.currentTimeMillis() + 8000;
        while (assistantMessages().isEmpty() && System.currentTimeMillis() < end) Thread.sleep(50);
        List<Map<String, Object>> saved = assistantMessages();
        assertEquals(1, saved.size(), "the partial answer was saved");
        assertEquals("STOPPED", saved.get(0).get("status"));
        String text = (String) saved.get(0).get("content");
        assertTrue(text.startsWith("Partial"), text);
        assertFalse(text.contains("finishes"), "it stopped before the end: " + text);
        Thread.sleep(300);
        assertEquals(1, assistantMessages().size(), "nothing is saved twice");
    }

    @Test
    void anErrorEventFromTheServiceIsForwardedAndNothingIsSavedAsComplete() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
                .withBody(sse("error", "{\"code\":\"invalid-draft\",\"detail\":\"The model did not return a valid draft\"}"))));
        String body = waitFor(stream("aisha.rahman", "Write a post"), "event:error");
        assertTrue(body.contains("invalid-draft"));
        assertTrue(assistantMessages().isEmpty());
    }

    @Test
    void aDownServiceDuringAStreamEndsWithAnAiUnavailableEvent() throws Exception {
        WM.stubFor(post("/v1/chat").willReturn(serverError()));
        String body = waitFor(stream("aisha.rahman", "hello"), "event:error");
        assertTrue(body.contains("ai-unavailable"), body);
    }

    @Test
    void anOpenBreakerAnswers503BeforeTheStreamStarts() throws Exception {
        for (int i = 0; i < 3; i++) breaker.failure();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/conversations/" + CONVERSATION + "/messages").header("Authorization", as("aisha.rahman"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content("{\"message\":\"hi\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable());
        WM.verify(0, postRequestedFor(urlEqualTo("/v1/chat")));
    }
}

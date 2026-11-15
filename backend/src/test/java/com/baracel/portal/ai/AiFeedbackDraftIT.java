package com.baracel.portal.ai;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Feedback on answers and editing a draft before publishing (stub mode). */
class AiFeedbackDraftIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    record Chat(String conversation, String answerId, String draftId) {}

    Chat chat(String user, String text) throws Exception {
        String conv = JsonPath.read(mvc.perform(postAs(user, "/api/ai/conversations", "{}")).andReturn().getResponse().getContentAsString(), "$.id");
        String res = mvc.perform(postAs(user, "/api/ai/conversations/" + conv + "/messages", "{\"message\":\"" + text + "\"}")).andReturn().getResponse().getContentAsString();
        return new Chat(conv, JsonPath.read(res, "$.assistantMessage.id"), JsonPath.read(res, "$.assistantMessage.draft.id"));
    }

    @Test
    void feedbackIsStoredOncePerPersonAndMessageAndOnlyForYourOwnConversations() throws Exception {
        Chat c = chat("aisha.rahman", "Write a post for QR payments");
        mvc.perform(postAs("aisha.rahman", "/api/ai/messages/" + c.answerId() + "/feedback", "{\"rating\":\"UP\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value("UP"));
        mvc.perform(postAs("aisha.rahman", "/api/ai/messages/" + c.answerId() + "/feedback", "{\"rating\":\"DOWN\",\"comment\":\"Too long\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value("DOWN")).andExpect(jsonPath("$.comment").value("Too long"));
        em.flush();
        assertEquals("DOWN|Too long", jdbc.queryForObject("select rating || '|' || comment from ai_feedback where message_id = ?::uuid", String.class, c.answerId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from ai_feedback where message_id = ?::uuid", Integer.class, c.answerId()));
        mvc.perform(postAs("aisha.rahman", "/api/ai/messages/" + c.answerId() + "/feedback", "{\"rating\":\"MEH\"}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("aisha.rahman", "/api/ai/messages/" + c.answerId() + "/feedback", "{\"rating\":\"UP\",\"comment\":\"" + "x".repeat(1001) + "\"}")).andExpect(status().isBadRequest());
        // someone else's conversation looks like it does not exist
        mvc.perform(postAs("omar.haddad", "/api/ai/messages/" + c.answerId() + "/feedback", "{\"rating\":\"UP\"}")).andExpect(status().isNotFound());
        mvc.perform(postAs("aisha.rahman", "/api/ai/messages/00000000-0000-0000-0000-000000000000/feedback", "{\"rating\":\"UP\"}")).andExpect(status().isNotFound());
        // the question is not an answer
        String question = jdbc.queryForObject("select id::text from ai_message where conversation_id = ?::uuid and role = 'USER'", String.class, c.conversation());
        mvc.perform(postAs("aisha.rahman", "/api/ai/messages/" + question + "/feedback", "{\"rating\":\"UP\"}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("sara.qureshi", "/api/ai/messages/" + c.answerId() + "/feedback", "{\"rating\":\"UP\"}")).andExpect(status().isNotFound());
    }

    @Test
    void theOwnerEditsAndPlatformLimitsApply() throws Exception {
        Chat c = chat("aisha.rahman", "Write a post for QR payments");
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"headline\":\"New headline\",\"caption\":\"A shorter caption.\",\"hashtags\":[\"Baracel\",\"#QR_Pay\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.headline").value("New headline")).andExpect(jsonPath("$.caption").value("A shorter caption."))
                .andExpect(jsonPath("$.hashtags", contains("#Baracel", "#QR_Pay")));
        // a partial edit leaves the rest alone
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"headline\":\"Only the headline\"}"))
                .andExpect(jsonPath("$.headline").value("Only the headline")).andExpect(jsonPath("$.caption").value("A shorter caption."));
        // LinkedIn: at most 3000 characters
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"caption\":\"" + "x".repeat(3000) + "\"}")).andExpect(status().isOk());
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"caption\":\"" + "x".repeat(3001) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/caption-too-long")));
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"headline\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"caption\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"hashtags\":[\"two words\"]}")).andExpect(status().isBadRequest());
        // only the owner
        mvc.perform(patchAs("omar.haddad", "/api/ai/drafts/" + c.draftId(), "{\"headline\":\"Hijack\"}")).andExpect(status().isNotFound());
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/00000000-0000-0000-0000-000000000000", "{\"headline\":\"x\"}")).andExpect(status().isNotFound());
        // and not after it was published
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + c.draftId() + "/publish", "{\"channels\":[\"company-linkedin\"]}")).andExpect(status().isOk());
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + c.draftId(), "{\"headline\":\"Too late\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", endsWith("/draft-published")));
    }

    @Test
    void streamingIsAvailableInStubModeToo() throws Exception {
        // the stub cannot stream, so the relay simulates the events: same contract for the frontend in local development
        String conv = JsonPath.read(mvc.perform(postAs("aisha.rahman", "/api/ai/conversations", "{}")).andReturn().getResponse().getContentAsString(), "$.id");
        var r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/conversations/" + conv + "/messages")
                .header("Authorization", as("aisha.rahman")).contentType("application/json").accept("text/event-stream").content("{\"message\":\"Write an ad post\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        long end = System.currentTimeMillis() + 5000;
        while (!r.getResponse().getContentAsString().contains("event:done") && System.currentTimeMillis() < end) Thread.sleep(25);
        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains("event:token") && body.contains("event:sources") && body.contains("event:draft") && body.contains("event:done"), body);
    }
}

package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.ai.service.AiService;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AiIT extends IntegrationTest {
    static final String AISHA_DRAFT = "f0000000-0000-4000-8000-000000000001";
    @Autowired AiService ai;
    @Autowired JdbcTemplate jdbc;

    String conversation(String user) throws Exception {
        return JsonPath.read(mvc.perform(postAs(user, "/api/ai/conversations", "{}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void salesGetsTheSampleDraftWithSourcesFromAllowedCollectionsOnly() throws Exception {
        String id = conversation("aisha.rahman");
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + id + "/messages",
                        "{\"message\":\"Write an Instagram and LinkedIn ad for our new QR product\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.assistantMessage.draft.headline").value("Scan. Pay. Done."))
                .andExpect(jsonPath("$.assistantMessage.draft.kind").value("SOCIAL_POST"))
                .andExpect(jsonPath("$.assistantMessage.draft.hashtags", contains("#Baracel", "#QRPayments", "#SmallBusiness")))
                .andExpect(jsonPath("$.assistantMessage.draft.suggestedChannels", contains("company-instagram", "company-linkedin")))
                .andExpect(jsonPath("$.assistantMessage.sources[*].collection", containsInAnyOrder("product", "brand")));
        mvc.perform(getAs("aisha.rahman", "/api/ai/conversations/" + id))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[1].draft.headline").value("Scan. Pay. Done."))
                .andExpect(jsonPath("$.title", startsWith("Write an Instagram")));
        mvc.perform(getAs("aisha.rahman", "/api/ai/conversations")).andExpect(jsonPath("$[0].title", startsWith("Write an Instagram")));
    }

    @Test
    void theStubNeverReturnsASourceFromACollectionTheUserLacks() throws Exception {
        // Priya (ENGINEER) may only search engineering. An ad request gets no draft and no sources.
        String priya = conversation("priya.nair");
        mvc.perform(postAs("priya.nair", "/api/ai/conversations/" + priya + "/messages", "{\"message\":\"Write an ad post for QR\"}"))
                .andExpect(jsonPath("$.assistantMessage.draft").doesNotExist())
                .andExpect(jsonPath("$.assistantMessage.sources", hasSize(0)));
        mvc.perform(postAs("priya.nair", "/api/ai/conversations/" + priya + "/messages", "{\"message\":\"How do deployments work?\"}"))
                .andExpect(jsonPath("$.assistantMessage.sources[*].collection", everyItem(is("engineering"))))
                .andExpect(jsonPath("$.assistantMessage.sources", hasSize(1)));
        // Sara has no knowledge collection at all
        String sara = conversation("sara.qureshi");
        mvc.perform(postAs("sara.qureshi", "/api/ai/conversations/" + sara + "/messages", "{\"message\":\"What is our leave policy?\"}"))
                .andExpect(jsonPath("$.assistantMessage.sources", hasSize(0)));
        // Aisha (SALES) never sees HR or engineering sources
        String aisha = conversation("aisha.rahman");
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + aisha + "/messages", "{\"message\":\"Tell me everything\"}"))
                .andExpect(jsonPath("$.assistantMessage.sources[*].collection", everyItem(anyOf(is("product"), is("brand"), is("campaign")))));
        // Daniel (ADMIN) has every collection
        String daniel = conversation("daniel.okoye");
        mvc.perform(postAs("daniel.okoye", "/api/ai/conversations/" + daniel + "/messages", "{\"message\":\"Tell me everything\"}"))
                .andExpect(jsonPath("$.assistantMessage.sources", hasSize(2)));
    }

    @Test
    void conversationsBelongToTheirOwner() throws Exception {
        String id = conversation("aisha.rahman");
        mvc.perform(getAs("omar.haddad", "/api/ai/conversations/" + id)).andExpect(status().isNotFound());
        mvc.perform(postAs("omar.haddad", "/api/ai/conversations/" + id + "/messages", "{\"message\":\"hi\"}")).andExpect(status().isNotFound());
        mvc.perform(postAs("aisha.rahman", "/api/ai/conversations/" + id + "/messages", "{\"message\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(getAs("omar.haddad", "/api/ai/conversations")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(getAs("aisha.rahman", "/api/ai/conversations")).andExpect(jsonPath("$", hasSize(5)));   // 4 seeded + this one
    }

    @Test
    void accessPanelListsAllowedAndDeniedCollectionsAndChannels() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/ai/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("SALES")))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].allowed").value(true))
                .andExpect(jsonPath("$.collections[?(@.code=='brand')].allowed").value(true))
                .andExpect(jsonPath("$.collections[?(@.code=='campaign')].allowed").value(true))
                .andExpect(jsonPath("$.collections[?(@.code=='hr-policies')].allowed").value(false))
                .andExpect(jsonPath("$.collections[?(@.code=='engineering')].allowed").value(false))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].docCount").value(214))
                .andExpect(jsonPath("$.companyChannels").value(3))
                .andExpect(jsonPath("$.personalChannels").value(1))
                .andExpect(jsonPath("$.channels[?(@.code=='personal-linkedin-aisha')].canPublish").value(true));
        // Priya sees the company channels but cannot publish to them, and does not see Aisha's personal account
        mvc.perform(getAs("priya.nair", "/api/ai/access"))
                .andExpect(jsonPath("$.collections[?(@.allowed==true)].code", contains("engineering")))
                .andExpect(jsonPath("$.channels[?(@.type=='COMPANY')].canPublish", everyItem(is(false))))
                .andExpect(jsonPath("$.channels[?(@.code=='personal-linkedin-aisha')]", hasSize(0)))
                .andExpect(jsonPath("$.personalChannels").value(0));
    }

    @Test
    void publishingIsSimulatedAndLoggedWithTheUser() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[\"company-instagram\",\"company-linkedin\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("SIMULATED"))))
                .andExpect(jsonPath("$[*].channel", contains("company-instagram", "company-linkedin")));
        List<String> rows = jdbc.queryForList("select user_name || ':' || channel_code || ':' || status from publish_log order by id", String.class);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("Aisha Rahman:company-instagram:SIMULATED", "Aisha Rahman:company-linkedin:SIMULATED"), rows);
        org.junit.jupiter.api.Assertions.assertEquals(TestUsers.id("aisha.rahman").toString(),
                jdbc.queryForObject("select distinct user_id::text from publish_log", String.class));
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[\"personal-linkedin-aisha\"]}")).andExpect(status().isOk());
    }

    @Test
    void publishValidation() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[\"company-tiktok\"]}")).andExpect(status().isBadRequest());
        // someone else's draft is invisible
        mvc.perform(postAs("omar.haddad", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[\"company-instagram\"]}")).andExpect(status().isNotFound());
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/00000000-0000-0000-0000-000000000000/publish", "{\"channels\":[\"company-instagram\"]}"))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from publish_log", Integer.class));
    }

    @Test
    void publishNeedsTheMatchingPermissionPerChannelType() {
        CurrentUser aisha = new CurrentUser(TestUsers.id("aisha.rahman"), TestUsers.id("aisha.rahman").toString(), "Aisha Rahman",
                "aisha.rahman@baracel.example", "ACTIVE", Set.of("EMPLOYEE", "SALES"), Set.of("ai:chat"));
        java.util.UUID draft = java.util.UUID.fromString(AISHA_DRAFT);
        ApiException e1 = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> ai.publish(aisha, draft, List.of("company-instagram")));
        org.junit.jupiter.api.Assertions.assertEquals(403, e1.status().value());
        // company permission alone does not allow a personal channel, and personal alone does not allow a company channel
        CurrentUser companyOnly = new CurrentUser(aisha.employeeId(), aisha.keycloakId(), aisha.name(), aisha.email(), "ACTIVE", aisha.roles(),
                Set.of("ai:chat", "social:publish:company"));
        org.junit.jupiter.api.Assertions.assertEquals(403, org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> ai.publish(companyOnly, draft, List.of("personal-linkedin-aisha"))).status().value());
        CurrentUser personalOnly = new CurrentUser(aisha.employeeId(), aisha.keycloakId(), aisha.name(), aisha.email(), "ACTIVE", aisha.roles(),
                Set.of("ai:chat", "social:publish:personal"));
        org.junit.jupiter.api.Assertions.assertEquals(403, org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> ai.publish(personalOnly, draft, List.of("company-facebook"))).status().value());
        org.junit.jupiter.api.Assertions.assertEquals(1, ai.publish(personalOnly, draft, List.of("personal-linkedin-aisha")).size());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from publish_log where channel_code like 'company%'", Integer.class));
    }

    @Test
    void aiNeedsTheChatPermission() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/access/roles/SALES/matrix", "{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/EMPLOYEE/matrix",
                "{\"permissions\":[\"profile:read-own\",\"leave:request\",\"employee:directory\",\"task:view\"]}")).andExpect(status().isOk());
        mvc.perform(getAs("sara.qureshi", "/api/ai/conversations")).andExpect(status().isForbidden());
        mvc.perform(getAs("sara.qureshi", "/api/ai/access")).andExpect(status().isForbidden());
    }
}

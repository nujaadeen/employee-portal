package com.baracel.portal.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baracel.portal.support.HttpModeIT;
import com.baracel.portal.support.TestUsers;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * LinkedIn connections and publishing against WireMock standing in for LinkedIn. Runs without a surrounding transaction so every
 * step commits like in production; {@link #cleanUp()} removes what the tests created.
 */
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class LinkedInIT extends HttpModeIT {
    static final String AISHA_DRAFT = "f0000000-0000-4000-8000-000000000001";
    static final String ACCESS = "AQT-access-secret-111", REFRESH = "AQT-refresh-secret-222";

    @Autowired JdbcTemplate jdbc;
    final List<UUID> conversations = new ArrayList<>();
    final List<String> bodies = new ArrayList<>();       // every API response body, searched for tokens at the end
    ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
        root.addAppender(logs);
        linkedInStubs();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from publish_log");
        jdbc.update("delete from social_oauth_state");
        jdbc.update("delete from social_connection");
        for (UUID c : conversations) jdbc.update("delete from ai_conversation where id = ?", c);   // cascades to messages and drafts
        jdbc.update("delete from social_channel where code like 'personal-linkedin-%' and code <> 'personal-linkedin-aisha'");
        jdbc.update("update ai_draft set caption = 'Your customers already scan to pay. Now your counter can too. Set up Baracel QR in minutes and see every payment in one place.', headline = 'Scan. Pay. Done.' where id = ?::uuid", AISHA_DRAFT);
        jdbc.update("delete from audit_log where action like 'SOCIAL_%' or action like 'AI_DRAFT_PUBLISH_%'");
    }

    @AfterEach
    void assertNoSecretsAnywhere() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAppender(logs);
        root.setLevel(Level.WARN);
        for (ILoggingEvent e : logs.list) {
            String text = e.getFormattedMessage() + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage());
            assertFalse(text.contains(ACCESS) || text.contains(REFRESH) || text.contains("li-secret"), "a token or secret was logged: " + text);
        }
        for (String b : bodies) assertFalse(b.contains(ACCESS) || b.contains(REFRESH), "a token was returned by an API: " + b);
    }

    void linkedInStubs() {
        WM.stubFor(post("/oauth/v2/accessToken").withRequestBody(containing("grant_type=authorization_code")).willReturn(okJson(
                "{\"access_token\":\"" + ACCESS + "\",\"expires_in\":5184000,\"refresh_token\":\"" + REFRESH + "\",\"refresh_token_expires_in\":31536000,\"scope\":\"openid,profile,w_member_social\"}")));
        WM.stubFor(get("/v2/userinfo").willReturn(okJson("{\"sub\":\"782bbtaQ\",\"name\":\"Aisha Rahman\"}")));
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(201).withHeader("x-restli-id", "urn:li:share:6844785523593134080")));
    }

    String body(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString();
        bodies.add(s);
        return s;
    }

    String start(String user, String target) throws Exception {
        return JsonPath.read(body(mvc.perform(postAs(user, "/api/social/linkedin/connect?target=" + target, "{}")).andExpect(status().isOk()).andReturn()), "$.state");
    }

    MvcResult callback(String state) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/social/linkedin/callback?code=auth-code-1&state=" + state)).andReturn();   // no bearer token: the browser comes from LinkedIn
    }

    void connectAisha() throws Exception {
        MvcResult r = callback(start("aisha.rahman", "personal"));
        assertEquals(302, r.getResponse().getStatus());
        assertThat(r.getResponse().getHeader("Location"), containsString("linkedin=connected"));
    }

    static void assertThat(String actual, org.hamcrest.Matcher<String> m) { org.hamcrest.MatcherAssert.assertThat(actual, m); }

    String publish(String user, String draft, String channel, int expectedStatus) throws Exception {
        return body(mvc.perform(postAs(user, "/api/ai/drafts/" + draft + "/publish", "{\"channels\":[\"" + channel + "\"]}")).andExpect(status().is(expectedStatus)).andReturn());
    }

    /** A draft owned by someone other than Aisha, created straight in the database. */
    String draftFor(String user) {
        UUID conv = UUID.randomUUID(), msg = UUID.randomUUID(), draft = UUID.randomUUID();
        conversations.add(conv);
        jdbc.update("insert into ai_conversation (id, owner_id, title) values (?, ?, 'x')", conv, TestUsers.id(user));
        jdbc.update("insert into ai_message (id, conversation_id, role, content) values (?, ?, 'ASSISTANT', 'draft')", msg, conv);
        jdbc.update("insert into ai_draft (id, message_id, kind, headline, caption, hashtags, suggested_channels, platform, language) values (?, ?, 'SOCIAL_POST', 'Headline', 'Caption', '#One', '', 'LINKEDIN', 'en')", draft, msg);
        return draft.toString();
    }

    // ---- connecting ----

    @Test
    void connectReturnsTheAuthorisationUrlWithAStateAndTheRightScopes() throws Exception {
        String res = body(mvc.perform(postAs("aisha.rahman", "/api/social/linkedin/connect?target=personal", "{}")).andExpect(status().isOk()).andReturn());
        String url = JsonPath.read(res, "$.authorizationUrl");
        String state = JsonPath.read(res, "$.state");
        assertThat(url, startsWith("http://localhost:" + WM.port() + "/oauth/v2/authorization?response_type=code&client_id=li-client"));
        assertThat(url, containsString("state=" + state));
        assertThat(url, containsString("w_member_social"));
        assertThat(url, not(containsString("w_organization_social")));
        assertEquals(1, jdbc.queryForObject("select count(*) from social_oauth_state where state = ? and used_at is null", Integer.class, state));
        // company scopes
        String company = JsonPath.read(body(mvc.perform(postAs("daniel.okoye", "/api/social/linkedin/connect?target=company", "{}")).andExpect(status().isOk()).andReturn()), "$.authorizationUrl");
        assertThat(company, containsString("w_organization_social"));
        assertThat(company, containsString("r_organization_admin"));
        // who may connect what
        mvc.perform(postAs("sara.qureshi", "/api/social/linkedin/connect?target=personal", "{}")).andExpect(status().isForbidden());   // no social:publish:personal
        mvc.perform(postAs("aisha.rahman", "/api/social/linkedin/connect?target=company", "{}")).andExpect(status().isForbidden());     // needs access:manage
        mvc.perform(postAs("aisha.rahman", "/api/social/linkedin/connect?target=other", "{}")).andExpect(status().isBadRequest());
    }

    @Test
    void theCallbackStoresEncryptedTokensAndNoApiReturnsThem() throws Exception {
        connectAisha();
        WM.verify(1, postRequestedFor(urlEqualTo("/oauth/v2/accessToken")).withRequestBody(containing("code=auth-code-1")).withRequestBody(containing("client_id=li-client")));
        List<String> enc = jdbc.queryForList("select access_token_enc || '|' || refresh_token_enc from social_connection where owner_id = ?", String.class, TestUsers.id("aisha.rahman"));
        assertEquals(1, enc.size());
        assertThat(enc.get(0), not(containsString(ACCESS)));
        assertThat(enc.get(0), not(containsString(REFRESH)));
        assertThat(enc.get(0), startsWith("k1:"));
        assertEquals("urn:li:person:782bbtaQ", jdbc.queryForObject("select account_urn from social_connection where owner_id = ?", String.class, TestUsers.id("aisha.rahman")));
        String list = body(mvc.perform(getAs("aisha.rahman", "/api/social/connections")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].type").value("PERSONAL")).andExpect(jsonPath("$[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$[0].displayName").value("Aisha Rahman")).andReturn());
        assertThat(list, not(containsString("token")));
        assertThat(list, not(containsString("Enc")));
        // the access panel now reports the channel as connected through LinkedIn
        mvc.perform(getAs("aisha.rahman", "/api/ai/access")).andExpect(jsonPath("$.channels[?(@.code=='personal-linkedin-aisha')].status").value("CONNECTED"));
    }

    @Test
    void aReplayedOrExpiredOrUnknownStateIsRejectedWithoutTouchingLinkedIn() throws Exception {
        String state = start("aisha.rahman", "personal");
        MvcResult first = callback(state);
        assertThat(first.getResponse().getHeader("Location"), containsString("linkedin=connected"));
        MvcResult replay = callback(state);
        assertThat(replay.getResponse().getHeader("Location"), allOf(containsString("linkedin=error"), containsString("reason=invalid-state")));
        // expired
        String expired = start("karim.aziz", "personal");
        jdbc.update("update social_oauth_state set expires_at = timestamptz '2000-01-01' where state = ?", expired);
        assertThat(callback(expired).getResponse().getHeader("Location"), containsString("reason=invalid-state"));
        // never issued
        assertThat(callback("not-a-real-state").getResponse().getHeader("Location"), containsString("reason=invalid-state"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/social/linkedin/callback?code=x")).andExpect(status().isFound());
        WM.verify(1, postRequestedFor(urlEqualTo("/oauth/v2/accessToken")));   // only the first, valid callback reached LinkedIn
        assertEquals(0, jdbc.queryForObject("select count(*) from social_connection where owner_id = ?", Integer.class, TestUsers.id("karim.aziz")));
    }

    @Test
    void theUserCancellingAtLinkedInIsNotAnError() throws Exception {
        String state = start("aisha.rahman", "personal");
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/social/linkedin/callback?error=user_cancelled_authorize&state=" + state)).andReturn();
        assertThat(r.getResponse().getHeader("Location"), containsString("linkedin=cancelled"));
        WM.verify(0, postRequestedFor(urlEqualTo("/oauth/v2/accessToken")));
    }

    @Test
    void theCompanyPageIsConnectedFromTheOrganisationsTheAdminAdministers() throws Exception {
        WM.stubFor(get(urlPathEqualTo("/rest/organizationAcls")).willReturn(okJson("{\"elements\":[{\"role\":\"ADMINISTRATOR\",\"state\":\"APPROVED\",\"organization\":\"urn:li:organization:5515715\"}]}")));
        WM.stubFor(get("/rest/organizations/5515715").willReturn(okJson("{\"localizedName\":\"Baracel\"}")));
        MvcResult r = callback(start("daniel.okoye", "company"));
        assertThat(r.getResponse().getHeader("Location"), allOf(containsString("target=company"), containsString("linkedin=connected")));
        WM.verify(getRequestedFor(urlPathEqualTo("/rest/organizationAcls")).withHeader("Linkedin-Version", equalTo("202610")).withHeader("X-Restli-Protocol-Version", equalTo("2.0.0")));
        mvc.perform(getAs("aisha.rahman", "/api/social/connections")).andExpect(jsonPath("$[?(@.type=='COMPANY')].displayName").value("Baracel"))   // SALES may use it
                .andExpect(jsonPath("$[?(@.type=='COMPANY')].canDisconnect").value(false))
                .andExpect(jsonPath("$[?(@.type=='COMPANY')].profileUrl").value("https://www.linkedin.com/company/5515715"));
        mvc.perform(getAs("sara.qureshi", "/api/social/connections")).andExpect(jsonPath("$", hasSize(0)));   // others do not see it
    }

    // ---- publishing ----

    @Test
    void aPersonalPostIsCreatedWithTheDocumentedRequestAndTheResultIsStored() throws Exception {
        connectAisha();
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[\"personal-linkedin-aisha\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].postUrl").value("https://www.linkedin.com/feed/update/urn:li:share:6844785523593134080/"));
        WM.verify(1, postRequestedFor(urlEqualTo("/rest/posts"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS))
                .withHeader("Linkedin-Version", equalTo("202610"))
                .withHeader("X-Restli-Protocol-Version", equalTo("2.0.0"))
                .withRequestBody(matchingJsonPath("$.author", equalTo("urn:li:person:782bbtaQ")))
                .withRequestBody(matchingJsonPath("$.visibility", equalTo("PUBLIC")))
                .withRequestBody(matchingJsonPath("$.lifecycleState", equalTo("PUBLISHED")))
                .withRequestBody(matchingJsonPath("$.distribution.feedDistribution", equalTo("MAIN_FEED")))
                .withRequestBody(matchingJsonPath("$.commentary", containing("Scan. Pay. Done.")))
                .withRequestBody(matchingJsonPath("$.commentary", containing("Your customers already scan to pay.")))
                .withRequestBody(matchingJsonPath("$.commentary", containing("#Baracel #QRPayments #SmallBusiness"))));
        assertEquals("PUBLISHED|urn:li:share:6844785523593134080", jdbc.queryForObject("select status || '|' || post_id from publish_log where channel_code = 'personal-linkedin-aisha'", String.class));
        mvc.perform(getAs("aisha.rahman", "/api/social/history")).andExpect(jsonPath("$.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[0].headline").value("Scan. Pay. Done.")).andExpect(jsonPath("$.items[0].channelName").value("My LinkedIn (personal)"))
                .andExpect(jsonPath("$.items[0].postUrl", startsWith("https://www.linkedin.com/feed/update/"))).andExpect(jsonPath("$.total").value(1));
        mvc.perform(getAs("karim.aziz", "/api/social/history")).andExpect(jsonPath("$.items", hasSize(0)));   // only your own
    }

    @Test
    void aDoubleClickPostsOnce() throws Exception {
        connectAisha();
        String first = publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200);
        String second = publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200);
        assertEquals(JsonPath.<String>read(first, "$[0].postUrl"), JsonPath.<String>read(second, "$[0].postUrl"));
        assertEquals("PUBLISHED", JsonPath.read(second, "$[0].status"));
        WM.verify(1, postRequestedFor(urlEqualTo("/rest/posts")));
        assertEquals(1, jdbc.queryForObject("select count(*) from publish_log where draft_id = ?::uuid", Integer.class, AISHA_DRAFT));
        mvc.perform(patchAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT, "{\"headline\":\"Edited after publish\"}")).andExpect(status().isConflict());
    }

    @Test
    void linkedInFailuresBecomeFailedReasonsAndOnlyDefiniteFailuresMayBeRetried() throws Exception {
        connectAisha();
        record Case(int status, String reason) {}
        for (Case c : List.of(new Case(401, "TOKEN_EXPIRED"), new Case(429, "RATE_LIMITED"), new Case(403, "REJECTED"), new Case(404, "UNKNOWN_ERROR"))) {
            jdbc.update("delete from publish_log");
            jdbc.update("update social_connection set status = 'CONNECTED' where owner_id = ?", TestUsers.id("aisha.rahman"));
            WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(c.status())));
            String res = publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200);
            assertEquals("FAILED", JsonPath.read(res, "$[0].status"), "status " + c.status());
            assertEquals(c.reason(), JsonPath.read(res, "$[0].reason"));
        }
        // a 401 marks the connection EXPIRED, so the person is asked to reconnect
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(401)));
        jdbc.update("delete from publish_log");
        jdbc.update("update social_connection set status = 'CONNECTED' where owner_id = ?", TestUsers.id("aisha.rahman"));
        publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200);
        assertEquals("EXPIRED", jdbc.queryForObject("select status from social_connection where owner_id = ?", String.class, TestUsers.id("aisha.rahman")));
        mvc.perform(getAs("aisha.rahman", "/api/social/connections")).andExpect(jsonPath("$[0].status").value("EXPIRED")).andExpect(jsonPath("$[0].canReconnect").value(true));
        // a definite failure can be tried again after reconnecting
        connectAisha();
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(201).withHeader("x-restli-id", "urn:li:share:999")));
        assertEquals("PUBLISHED", JsonPath.read(publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200), "$[0].status"));
        assertEquals(1, jdbc.queryForObject("select count(*) from publish_log where draft_id = ?::uuid", Integer.class, AISHA_DRAFT));
    }

    @Test
    void aTimeoutOrAnUnclearAnswerIsUnknownAndIsNeverRetried() throws Exception {
        connectAisha();
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(201).withHeader("x-restli-id", "urn:li:share:1").withFixedDelay(3000)));   // read timeout is 1 s in tests
        String res = publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200);
        assertEquals("UNKNOWN", JsonPath.read(res, "$[0].status"));
        assertThat(JsonPath.read(res, "$[0].message"), containsString("Check LinkedIn"));
        WM.verify(1, postRequestedFor(urlEqualTo("/rest/posts")));
        // asking again returns the same UNKNOWN and does not post again
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(201).withHeader("x-restli-id", "urn:li:share:2")));
        assertEquals("UNKNOWN", JsonPath.read(publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200), "$[0].status"));
        WM.verify(1, postRequestedFor(urlEqualTo("/rest/posts")));
        // a 5xx or a 201 without a post id is also unclear
        jdbc.update("delete from publish_log");
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(503)));
        assertEquals("UNKNOWN", JsonPath.read(publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200), "$[0].status"));
        jdbc.update("delete from publish_log");
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withStatus(201)));
        assertEquals("UNKNOWN", JsonPath.read(publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200), "$[0].status"));
        jdbc.update("delete from publish_log");
        WM.stubFor(post("/rest/posts").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertEquals("UNKNOWN", JsonPath.read(publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200), "$[0].status"));
    }

    @Test
    void anExpiredAccessTokenIsRefreshedWhenLinkedInIssuedARefreshTokenAndMarkedExpiredWhenThatFails() throws Exception {
        connectAisha();
        jdbc.update("update social_connection set expires_at = timestamptz '2000-01-01' where owner_id = ?", TestUsers.id("aisha.rahman"));
        WM.stubFor(post("/oauth/v2/accessToken").withRequestBody(containing("grant_type=refresh_token")).willReturn(okJson(
                "{\"access_token\":\"AQT-new-access-333\",\"expires_in\":5184000,\"refresh_token\":\"AQT-refresh-secret-222\"}")));
        assertEquals("PUBLISHED", JsonPath.read(publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200), "$[0].status"));
        WM.verify(postRequestedFor(urlEqualTo("/rest/posts")).withHeader("Authorization", equalTo("Bearer AQT-new-access-333")));
        WM.verify(postRequestedFor(urlEqualTo("/oauth/v2/accessToken")).withRequestBody(containing("grant_type=refresh_token")).withRequestBody(containing("refresh_token=" + REFRESH)));
        // refresh fails: the connection becomes EXPIRED and the result is FAILED TOKEN_EXPIRED
        jdbc.update("delete from publish_log");
        jdbc.update("update social_connection set expires_at = timestamptz '2000-01-01' where owner_id = ?", TestUsers.id("aisha.rahman"));
        WM.stubFor(post("/oauth/v2/accessToken").withRequestBody(containing("grant_type=refresh_token")).willReturn(aResponse().withStatus(400)));
        String res = publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200);
        assertEquals("TOKEN_EXPIRED", JsonPath.read(res, "$[0].reason"));
        assertEquals("EXPIRED", jdbc.queryForObject("select status from social_connection where owner_id = ?", String.class, TestUsers.id("aisha.rahman")));
    }

    @Test
    void aUserCannotPublishThroughSomeoneElsesConnection() throws Exception {
        connectAisha();   // Aisha is connected
        String karimDraft = draftFor("karim.aziz");   // Karim (SALES) has the personal publish permission but his own draft and no connection
        publish("karim.aziz", karimDraft, "personal-linkedin-aisha", 403);
        WM.verify(0, postRequestedFor(urlEqualTo("/rest/posts")));
        // Karim sees none of Aisha's connections and cannot remove them
        mvc.perform(getAs("karim.aziz", "/api/social/connections")).andExpect(jsonPath("$", hasSize(0)));
        UUID aishaConn = jdbc.queryForObject("select id from social_connection where owner_id = ?", UUID.class, TestUsers.id("aisha.rahman"));
        mvc.perform(deleteAs("karim.aziz", "/api/social/connections/" + aishaConn)).andExpect(status().isNotFound());
        // his own channel exists but is not connected
        assertEquals(List.of("NOT_CONNECTED"), JsonPath.read(body(mvc.perform(getAs("karim.aziz", "/api/ai/access")).andReturn()), "$.channels[?(@.type=='PERSONAL')].status"));
        mvc.perform(postAs("karim.aziz", "/api/ai/drafts/" + karimDraft + "/publish", "{\"channels\":[\"personal-linkedin-" + TestUsers.id("karim.aziz").toString().substring(0, 8) + "\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type", endsWith("/channel-not-connected")));
        // and the draft is not shared: Aisha's draft is invisible to Karim
        publish("karim.aziz", AISHA_DRAFT, "personal-linkedin-aisha", 404);
    }

    @Test
    void theCompanyPageNeedsTheCompanyPermissionAndTheSwitch() throws Exception {
        WM.stubFor(get(urlPathEqualTo("/rest/organizationAcls")).willReturn(okJson("{\"elements\":[{\"organization\":\"urn:li:organization:5515715\"}]}")));
        callback(start("daniel.okoye", "company"));
        // Aisha (SALES: social:publish:company) publishes to the company page
        assertEquals("PUBLISHED", JsonPath.read(publish("aisha.rahman", AISHA_DRAFT, "company-linkedin", 200), "$[0].status"));
        WM.verify(postRequestedFor(urlEqualTo("/rest/posts")).withRequestBody(matchingJsonPath("$.author", equalTo("urn:li:organization:5515715"))));
        // Priya (ENGINEER) cannot
        publish("priya.nair", draftFor("priya.nair"), "company-linkedin", 403);
        // Only an admin disconnects the company page
        UUID conn = jdbc.queryForObject("select id from social_connection where owner_id is null", UUID.class);
        mvc.perform(deleteAs("aisha.rahman", "/api/social/connections/" + conn)).andExpect(status().isForbidden());
        mvc.perform(deleteAs("daniel.okoye", "/api/social/connections/" + conn)).andExpect(status().isNoContent());
        assertEquals("", jdbc.queryForObject("select access_token_enc from social_connection where id = ?", String.class, conn));   // tokens are wiped
        publish("aisha.rahman", draftFor("aisha.rahman"), "company-linkedin", 409);
    }

    @Test
    void instagramAndFacebookAreComingLater() throws Exception {
        connectAisha();
        for (String code : List.of("company-instagram", "company-facebook")) {
            mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[\"" + code + "\"]}"))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.type", endsWith("/channel-not-available")));
        }
        // a list with one good and one unavailable channel publishes nothing
        mvc.perform(postAs("aisha.rahman", "/api/ai/drafts/" + AISHA_DRAFT + "/publish", "{\"channels\":[\"personal-linkedin-aisha\",\"company-instagram\"]}")).andExpect(status().isUnprocessableContent());
        WM.verify(0, postRequestedFor(urlEqualTo("/rest/posts")));
        mvc.perform(getAs("aisha.rahman", "/api/ai/access")).andExpect(jsonPath("$.channels[?(@.code=='company-instagram')].status").value("COMING_LATER"))
                .andExpect(jsonPath("$.channels[?(@.code=='company-instagram')].canPublish").value(false));
    }

    @Test
    void personalDisconnectRemovesTheTokensAndOnlyTheOwnerMayDoIt() throws Exception {
        connectAisha();
        UUID conn = jdbc.queryForObject("select id from social_connection where owner_id = ?", UUID.class, TestUsers.id("aisha.rahman"));
        mvc.perform(deleteAs("aisha.rahman", "/api/social/connections/" + conn)).andExpect(status().isNoContent());
        assertEquals("REVOKED|", jdbc.queryForObject("select status || '|' || access_token_enc from social_connection where id = ?", String.class, conn));
        assertNull(jdbc.queryForObject("select refresh_token_enc from social_connection where id = ?", String.class, conn));
        mvc.perform(getAs("aisha.rahman", "/api/social/connections")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(deleteAs("aisha.rahman", "/api/social/connections/" + conn)).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where action = 'SOCIAL_DISCONNECTED'", Integer.class));
    }

    @Test
    void anOverlongPostIsRejectedBeforeLinkedInIsCalled() throws Exception {
        connectAisha();
        jdbc.update("update ai_draft set caption = ? where id = ?::uuid", "x".repeat(2999) + "*", AISHA_DRAFT);   // the * is escaped, which makes it too long
        String res = publish("aisha.rahman", AISHA_DRAFT, "personal-linkedin-aisha", 200);
        assertEquals("REJECTED", JsonPath.read(res, "$[0].reason"));
        WM.verify(0, postRequestedFor(urlEqualTo("/rest/posts")));
    }
}

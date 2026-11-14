package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.FakeGiteaClient;
import com.baracel.portal.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class GiteaIT extends IntegrationTest {
    static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    ResultActions webhook(String event, String body) throws Exception {
        return mvc.perform(post("/api/integrations/gitea/webhook").header("X-Gitea-Event", event)
                .header("X-Gitea-Signature", sign(body)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static String pr(String action, int number, String branch, String title, boolean merged, String sha) {
        return """
            {"action":"%s","number":%d,"pull_request":{"number":%d,"title":"%s","body":"","state":"%s","merged":%s,
             "html_url":"http://gitea.test/baracel/otp-service/pulls/%d","head":{"ref":"%s","sha":"%s"}},
             "repository":{"full_name":"baracel/otp-service"},"sender":{"login":"aisha.rahman"}}
            """.formatted(action, number, number, title, action.equals("closed") ? "closed" : "open", merged, number, branch, sha);
    }

    @Test
    void wrongOrMissingSignatureIsRejected() throws Exception {
        String body = pr("opened", 99, "feature/bar-142-hotfix", "Hotfix", false, "aaa111");
        mvc.perform(post("/api/integrations/gitea/webhook").header("X-Gitea-Event", "pull_request")
                        .header("X-Gitea-Signature", "deadbeef").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.type", endsWith("/invalid-signature")));
        mvc.perform(post("/api/integrations/gitea/webhook").header("X-Gitea-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // a signature made for another body does not work either
        mvc.perform(post("/api/integrations/gitea/webhook").header("X-Gitea-Event", "pull_request")
                        .header("X-Gitea-Signature", sign(body + " ")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // nothing was linked
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142")).andExpect(jsonPath("$.git.links[?(@.prNumber==99)]", hasSize(0)));
    }

    @Test
    void signedPullRequestLinksToBar142AndMovesItToReview() throws Exception {
        webhook("pull_request", pr("opened", 99, "feature/bar-142-hotfix", "Hotfix for OTP", false, "aaa111bbb222"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tasksUpdated").value(1));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142"))
                .andExpect(jsonPath("$.task.task.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.git.links[?(@.prNumber==99)].prState").value("OPEN"))
                .andExpect(jsonPath("$.git.links[?(@.prNumber==99)].branch").value("feature/bar-142-hotfix"))
                .andExpect(jsonPath("$.task.activity[*].message", hasItem("opened pull request #99 in baracel/otp-service")));
    }

    @Test
    void mergedPullRequestMovesInReviewToDone() throws Exception {
        // BAR-138 is IN_REVIEW in the seed (PR 54)
        webhook("pull_request", pr("closed", 54, "feature/bar-138-static-qr", "Static QR code generation", true, "5e7f9a1b3c4d6e8f0a2b4c6d8e0f1a3b5c7d9e1f"))
                .andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-138"))
                .andExpect(jsonPath("$.task.task.status").value("DONE"))
                .andExpect(jsonPath("$.git.links[0].prState").value("MERGED"));
    }

    @Test
    void taskKeyInTheTitleIsEnoughAndOtherStatusesAreLeftAlone() throws Exception {
        // BAR-151 is BACKLOG: opening a PR must not move it
        webhook("pull_request", pr("opened", 120, "fix/typo", "BAR-151: telegram fallback", false, "c0ffee"))
                .andExpect(jsonPath("$.tasksUpdated").value(1));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-151"))
                .andExpect(jsonPath("$.task.task.status").value("BACKLOG"))
                .andExpect(jsonPath("$.git.links[0].prNumber").value(120));
        // a PR without a known task key links nothing
        webhook("pull_request", pr("opened", 121, "chore/deps", "Bump libs for XYZ-1", false, "c0ffee2"))
                .andExpect(jsonPath("$.tasksUpdated").value(0));
    }

    @Test
    void pushAddsCommitsFromBranchAndMessage() throws Exception {
        String body = """
            {"ref":"refs/heads/feature/bar-146-webhook-retries","after":"f00d1234f00d1234f00d1234f00d1234f00d1234",
             "repository":{"full_name":"baracel/otp-service"},"pusher":{"login":"aisha.rahman"},
             "commits":[{"id":"f00d1234f00d1234f00d1234f00d1234f00d1234","message":"Add dead letter queue\\n\\nmore text","url":"http://gitea.test/c/f00d123",
                         "timestamp":"2026-11-13T09:00:00+04:00","author":{"name":"Aisha Rahman","username":"aisha.rahman"}},
                        {"id":"beef5678beef5678beef5678beef5678beef5678","message":"Retry tuning for BAR-151","timestamp":"2026-11-13T09:05:00+04:00","author":{"name":"Aisha Rahman"}}]}
            """;
        webhook("push", body).andExpect(status().isOk()).andExpect(jsonPath("$.tasksUpdated").value(2));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-146"))
                .andExpect(jsonPath("$.git.commits", hasSize(2)))
                .andExpect(jsonPath("$.git.links[0].lastSha").value("f00d1234f00d1234f00d1234f00d1234f00d1234"))
                .andExpect(jsonPath("$.task.activity[0].message").value("pushed 2 commits to feature/bar-146-webhook-retries in baracel/otp-service"));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-151"))
                .andExpect(jsonPath("$.git.commits", hasSize(1)))
                .andExpect(jsonPath("$.git.commits[0].message").value("Retry tuning for BAR-151"));
        // the same delivery twice does not duplicate commits
        webhook("push", body).andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-146")).andExpect(jsonPath("$.git.commits", hasSize(2)));
    }

    @Test
    void createEventLinksTheBranch() throws Exception {
        webhook("create", "{\"ref\":\"feature/bar-153-checklist\",\"ref_type\":\"branch\",\"sha\":\"abc\",\"repository\":{\"full_name\":\"baracel/portal\"},\"sender\":{\"login\":\"sara.qureshi\"}}")
                .andExpect(jsonPath("$.tasksUpdated").value(1));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-153"))
                .andExpect(jsonPath("$.git.links[0].branch").value("feature/bar-153-checklist"))
                .andExpect(jsonPath("$.task.activity[0].actor").value("Sara Qureshi"));
        webhook("create", "{\"ref\":\"v1.0\",\"ref_type\":\"tag\",\"repository\":{\"full_name\":\"baracel/portal\"}}")
                .andExpect(jsonPath("$.tasksUpdated").value(0));
    }

    @Test
    void statusEventUpdatesChecksOnTheLinkedPullRequest() throws Exception {
        String sha = "6f8a0b2c4d5e7f9a1b3c5d7e9f0a2b4c6d8e0f1a";   // BAR-140, FAILING in the seed
        webhook("status", "{\"sha\":\"" + sha + "\",\"state\":\"success\",\"context\":\"ci\",\"repository\":{\"full_name\":\"baracel/portal\"}}")
                .andExpect(jsonPath("$.tasksUpdated").value(1));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-140")).andExpect(jsonPath("$.git.links[0].checksStatus").value("PASSING"));
        webhook("status", "{\"sha\":\"" + sha + "\",\"state\":\"failure\",\"repository\":{\"full_name\":\"baracel/portal\"}}");
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-140")).andExpect(jsonPath("$.git.links[0].checksStatus").value("FAILING"));
    }

    @Test
    void reviewEventsFeedTheDashboardAndApprovalCounts() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/dashboard")).andExpect(jsonPath("$.pullRequestsAwaitingReview").value(3));
        webhook("pull_request", "{\"action\":\"review_requested\",\"pull_request\":{\"number\":70,\"title\":\"Fix\",\"html_url\":\"u\",\"head\":{\"ref\":\"x\"},\"state\":\"open\"},"
                + "\"requested_reviewer\":{\"login\":\"aisha.rahman\"},\"repository\":{\"full_name\":\"baracel/portal\"},\"sender\":{\"login\":\"daniel.okoye\"}}")
                .andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/dashboard")).andExpect(jsonPath("$.pullRequestsAwaitingReview").value(4));
        // Aisha reviews PR 70: it is no longer waiting for her
        webhook("pull_request_approved", "{\"action\":\"reviewed\",\"review\":{\"type\":\"pull_request_review_approved\"},\"pull_request\":{\"number\":70,\"title\":\"Fix\",\"head\":{\"ref\":\"x\"}},"
                + "\"repository\":{\"full_name\":\"baracel/portal\"},\"sender\":{\"login\":\"aisha.rahman\"}}").andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/dashboard")).andExpect(jsonPath("$.pullRequestsAwaitingReview").value(3));
        // an approval on BAR-138's PR counts and is logged
        webhook("pull_request_approved", "{\"action\":\"reviewed\",\"review\":{\"type\":\"pull_request_review_approved\"},\"pull_request\":{\"number\":54,\"title\":\"Static QR code generation\",\"head\":{\"ref\":\"feature/bar-138-static-qr\"}},"
                + "\"repository\":{\"full_name\":\"baracel/otp-service\"},\"sender\":{\"login\":\"daniel.okoye\"}}").andExpect(jsonPath("$.tasksUpdated").value(1));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-138"))
                .andExpect(jsonPath("$.git.links[0].approvals").value(1))
                .andExpect(jsonPath("$.task.activity[0].message").value("approved pull request #54 in baracel/otp-service"))
                .andExpect(jsonPath("$.task.activity[0].actor").value("Daniel Okoye"));
        // closing PR 61 clears the waiting review
        webhook("pull_request", pr("closed", 61, "x", "Add leave export endpoint", false, "s").replace("baracel/otp-service", "baracel/portal"));
        mvc.perform(getAs("aisha.rahman", "/api/dashboard")).andExpect(jsonPath("$.pullRequestsAwaitingReview").value(2));
    }

    @Test
    void syncLabelAndReviewRequestEventsAreNotTreatedAsReviews() throws Exception {
        // new commits pushed to PR 57 (BAR-142): sha moves, checks reset, no review side effects, no activity
        webhook("pull_request_sync", pr("synchronized", 57, "feature/bar-142-whatsapp-otp", "Add WhatsApp OTP delivery", false, "999aaa"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tasksUpdated").value(1));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142"))
                .andExpect(jsonPath("$.git.links[?(@.prNumber==57)].lastSha").value("999aaa"))
                .andExpect(jsonPath("$.git.links[?(@.prNumber==57)].approvals").value(0))
                .andExpect(jsonPath("$.task.activity[0].message", not(containsString("reviewed"))));
        mvc.perform(getAs("aisha.rahman", "/api/dashboard")).andExpect(jsonPath("$.pullRequestsAwaitingReview").value(3));
        webhook("pull_request_label", pr("label_updated", 57, "feature/bar-142-whatsapp-otp", "Add WhatsApp OTP delivery", false, "999aaa")).andExpect(status().isOk());
        webhook("pull_request_review_request", "{\"action\":\"review_requested\",\"pull_request\":{\"number\":71,\"title\":\"Y\",\"head\":{\"ref\":\"y\"},\"state\":\"open\"},"
                + "\"requested_reviewer\":{\"login\":\"aisha.rahman\"},\"repository\":{\"full_name\":\"baracel/portal\"},\"sender\":{\"login\":\"daniel.okoye\"}}").andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/dashboard")).andExpect(jsonPath("$.pullRequestsAwaitingReview").value(4));
    }

    @Test
    void realGiteaSendsNullReviewOnPlainPullRequestEvents() throws Exception {
        // review request: payload carries "review": null and must still register the pending review
        webhook("pull_request", "{\"action\":\"review_requested\",\"review\":null,\"pull_request\":{\"number\":72,\"title\":\"Z\",\"head\":{\"ref\":\"z\"},\"state\":\"open\"},"
                + "\"requested_reviewer\":{\"login\":\"omar.haddad\"},\"repository\":{\"full_name\":\"baracel/portal\"},\"sender\":{\"login\":\"aisha.rahman\"}}").andExpect(status().isOk());
        mvc.perform(getAs("omar.haddad", "/api/dashboard")).andExpect(jsonPath("$.pullRequestsAwaitingReview").value(1));
        // merge: "review": null on a closed+merged event still moves BAR-138 to Done
        webhook("pull_request", pr("closed", 54, "feature/bar-138-static-qr", "Static QR code generation", true, "5e7f9a1b3c4d6e8f0a2b4c6d8e0f1a3b5c7d9e1f").replace("{\"action\"", "{\"review\":null,\"action\""))
                .andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-138")).andExpect(jsonPath("$.task.task.status").value("DONE"));
    }

    @Test
    void unknownEventsAreAcknowledged() throws Exception {
        webhook("issues", "{\"action\":\"opened\"}").andExpect(status().isOk()).andExpect(jsonPath("$.tasksUpdated").value(0));
    }

    @Test
    void createBranchNamesItAfterTheTaskAndCallsGitea() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/branch", "{\"repo\":\"baracel/portal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("feature/bar-146-partner-webhook-retries"))
                .andExpect(jsonPath("$.repo").value("baracel/portal"));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("baracel/portal#feature/bar-146-partner-webhook-retries<-main"),
                ((FakeGiteaClient) gitea).branches);
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-146")).andExpect(jsonPath("$.task.activity[0].message", startsWith("created branch")));
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/branch", "{}")).andExpect(status().isBadRequest());   // two repos, none named
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/branch", "{\"repo\":\"missing/nope\"}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("karim.aziz", "/api/tasks/BAR-146/git/branch", "{\"repo\":\"baracel/portal\"}")).andExpect(status().isNotFound());
        ((FakeGiteaClient) gitea).failBranch = true;
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/branch", "{\"repo\":\"baracel/portal\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.type", endsWith("/gitea-unavailable")));
    }

    @Test
    void linkPullRequestFetchesItFromGitea() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/link-pr", "{\"repo\":\"baracel/otp-service\",\"prNumber\":77}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prNumber").value(77))
                .andExpect(jsonPath("$.prState").value("OPEN"))
                .andExpect(jsonPath("$.checksStatus").value("PASSING"))
                .andExpect(jsonPath("$.branch").value("feature/bar-146-webhook-retries"));
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/link-pr", "{\"repo\":\"baracel/otp-service\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void repositoriesNeedRepoView() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/repos")).andExpect(status().isForbidden());      // SALES has no repo:view
        mvc.perform(getAs("karim.aziz", "/api/repos")).andExpect(status().isForbidden());
        mvc.perform(getAs("priya.nair", "/api/repos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fullName", contains("baracel/otp-service", "baracel/portal")));
        mvc.perform(getAs("priya.nair", "/api/repos?refresh=true")).andExpect(jsonPath("$[0].htmlUrl").value("http://gitea.test/baracel/otp-service"));
    }
}

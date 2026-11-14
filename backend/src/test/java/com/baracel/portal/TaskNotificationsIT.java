package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Task and Gitea events notify the right people (bell notification; the mail goes through the same service). */
class TaskNotificationsIT extends IntegrationTest {

    private void webhook(String event, String body) throws Exception {
        mvc.perform(post("/api/integrations/gitea/webhook").header("X-Gitea-Event", event).header("X-Gitea-Signature", GiteaIT.sign(body))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test
    void assigningATaskNotifiesTheAssigneeButNotYourself() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/tasks", "{\"projectKey\":\"BAR\",\"title\":\"Alert Priya\",\"assigneeId\":\"" + TestUsers.id("priya.nair") + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(getAs("priya.nair", "/api/notifications"))
                .andExpect(jsonPath("$.items[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.items[0].title").value("BAR-158 was assigned to you"))
                .andExpect(jsonPath("$.items[0].link").value("/tasks/BAR-158"));
        // self-assignment and reassignment
        mvc.perform(postAs("aisha.rahman", "/api/tasks", "{\"projectKey\":\"BAR\",\"title\":\"Mine\",\"assigneeId\":\"" + TestUsers.id("aisha.rahman") + "\"}")).andExpect(status().isCreated());
        mvc.perform(getAs("aisha.rahman", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(patchAs("aisha.rahman", "/api/tasks/BAR-146", "{\"assigneeId\":\"" + TestUsers.id("mei.tan") + "\"}")).andExpect(status().isOk());
        mvc.perform(getAs("mei.tan", "/api/notifications")).andExpect(jsonPath("$.items[0].title").value("BAR-146 was assigned to you"));
    }

    @Test
    void commentsNotifyAssigneeAndReporterExceptTheAuthor() throws Exception {
        // BAR-142: assignee Aisha, reporter Omar
        mvc.perform(postAs("daniel.okoye", "/api/tasks/BAR-142/comments", "{\"body\":\"Please add tests\"}")).andExpect(status().isCreated());
        mvc.perform(getAs("aisha.rahman", "/api/notifications")).andExpect(jsonPath("$.items[0].type").value("TASK_COMMENT")).andExpect(jsonPath("$.items[0].body").value("Please add tests"));
        mvc.perform(getAs("omar.haddad", "/api/notifications")).andExpect(jsonPath("$.items[0].type").value("TASK_COMMENT"));
        mvc.perform(getAs("daniel.okoye", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(postAs("omar.haddad", "/api/tasks/BAR-142/comments", "{\"body\":\"Thanks\"}")).andExpect(status().isCreated());
        mvc.perform(getAs("aisha.rahman", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void reviewRequestNotifiesTheReviewer() throws Exception {
        webhook("pull_request", "{\"action\":\"review_requested\",\"review\":null,\"pull_request\":{\"number\":72,\"title\":\"BAR-146 retries\",\"head\":{\"ref\":\"x\"},\"state\":\"open\"},"
                + "\"requested_reviewer\":{\"login\":\"omar.haddad\"},\"repository\":{\"full_name\":\"baracel/otp-service\"},\"sender\":{\"login\":\"aisha.rahman\"}}");
        mvc.perform(getAs("omar.haddad", "/api/notifications"))
                .andExpect(jsonPath("$.items[0].type").value("PR_REVIEW_REQUESTED"))
                .andExpect(jsonPath("$.items[0].title").value("Review requested on PR #72"))
                .andExpect(jsonPath("$.items[0].link").value("/tasks/BAR-146"));
        // asking yourself notifies nobody
        webhook("pull_request", "{\"action\":\"review_requested\",\"pull_request\":{\"number\":73,\"title\":\"x\",\"head\":{\"ref\":\"x\"},\"state\":\"open\"},"
                + "\"requested_reviewer\":{\"login\":\"aisha.rahman\"},\"repository\":{\"full_name\":\"baracel/portal\"},\"sender\":{\"login\":\"aisha.rahman\"}}");
        mvc.perform(getAs("aisha.rahman", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void approvalNotifiesTheAuthorAndAssignee() throws Exception {
        // PR 54 belongs to BAR-138 (assignee Tom); Tom opened it. Daniel approves.
        webhook("pull_request_approved", "{\"action\":\"reviewed\",\"review\":{\"type\":\"pull_request_review_approved\"},\"pull_request\":{\"number\":54,\"title\":\"Static QR code generation\","
                + "\"user\":{\"login\":\"tom.becker\"},\"head\":{\"ref\":\"feature/bar-138-static-qr\"}},\"repository\":{\"full_name\":\"baracel/otp-service\"},\"sender\":{\"login\":\"daniel.okoye\"}}");
        mvc.perform(getAs("tom.becker", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("PR_APPROVED")).andExpect(jsonPath("$.items[0].title").value("PR #54 approved by Daniel Okoye"));
        mvc.perform(getAs("daniel.okoye", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(0)));
        webhook("pull_request_rejected", "{\"action\":\"reviewed\",\"review\":{\"type\":\"pull_request_review_rejected\"},\"pull_request\":{\"number\":54,\"title\":\"Static QR code generation\","
                + "\"user\":{\"login\":\"tom.becker\"},\"head\":{\"ref\":\"feature/bar-138-static-qr\"}},\"repository\":{\"full_name\":\"baracel/otp-service\"},\"sender\":{\"login\":\"daniel.okoye\"}}");
        mvc.perform(getAs("tom.becker", "/api/notifications")).andExpect(jsonPath("$.items[0].type").value("PR_CHANGES_REQUESTED"));
        // a plain review comment does not notify
        webhook("pull_request_comment", "{\"action\":\"reviewed\",\"review\":{\"type\":\"pull_request_review_comment\"},\"pull_request\":{\"number\":54,\"title\":\"t\",\"user\":{\"login\":\"tom.becker\"},\"head\":{\"ref\":\"feature/bar-138-static-qr\"}},"
                + "\"repository\":{\"full_name\":\"baracel/otp-service\"},\"sender\":{\"login\":\"daniel.okoye\"}}");
        mvc.perform(getAs("tom.becker", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void openedAndMergedPullRequestsNotifyAssigneeAndReporter() throws Exception {
        // BAR-142: assignee Aisha opens a PR, reporter Omar is told (Aisha did it herself)
        webhook("pull_request", GiteaIT.pr("opened", 99, "feature/bar-142-hotfix", "Hotfix", false, "aaa111"));
        mvc.perform(getAs("omar.haddad", "/api/notifications")).andExpect(jsonPath("$.items[0].type").value("PR_OPENED"))
                .andExpect(jsonPath("$.items[0].body", containsString("moved to In review")));
        mvc.perform(getAs("aisha.rahman", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(0)));
        // BAR-138 merged by Daniel: assignee Tom and reporter Omar are told, the task is Done
        webhook("pull_request", GiteaIT.pr("closed", 54, "feature/bar-138-static-qr", "Static QR code generation", true, "5e7f9a").replace("aisha.rahman", "daniel.okoye"));
        mvc.perform(getAs("tom.becker", "/api/notifications")).andExpect(jsonPath("$.items[0].type").value("PR_MERGED")).andExpect(jsonPath("$.items[0].body", containsString("now Done")));
        mvc.perform(getAs("omar.haddad", "/api/notifications")).andExpect(jsonPath("$.items[0].type").value("PR_MERGED"));
        mvc.perform(getAs("daniel.okoye", "/api/notifications")).andExpect(jsonPath("$.items", hasSize(0)));
    }
}

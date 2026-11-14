package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

class TaskIT extends IntegrationTest {

    @Test
    void projectsAreVisibleOnlyToMembers() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value("BAR"))
                .andExpect(jsonPath("$[0].name").value("Platform"))
                .andExpect(jsonPath("$[0].activeSprint.name").value("Sprint 23"));
        // Karim is not a member of BAR
        mvc.perform(getAs("karim.aziz", "/api/projects")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(getAs("karim.aziz", "/api/projects/BAR/board")).andExpect(status().isNotFound());
        mvc.perform(getAs("karim.aziz", "/api/tasks/BAR-142")).andExpect(status().isNotFound());
        mvc.perform(patchAs("karim.aziz", "/api/tasks/BAR-142/status", "{\"status\":\"DONE\"}")).andExpect(status().isNotFound());
        mvc.perform(postAs("karim.aziz", "/api/tasks", "{\"projectKey\":\"BAR\",\"title\":\"Sneaky\"}")).andExpect(status().isNotFound());
    }

    @Test
    void boardMatchesSprint23() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/projects/BAR/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprint.name").value("Sprint 23"))
                .andExpect(jsonPath("$.taskCount").value(11))
                .andExpect(jsonPath("$.linkedToGitea").value(7))
                .andExpect(jsonPath("$.lastSyncedAt").exists())
                .andExpect(jsonPath("$.columns[*].count", contains(3, 3, 2, 3)))
                .andExpect(jsonPath("$.columns[0].label").value("Backlog"))
                .andExpect(jsonPath("$.columns[0].tasks[*].task.key", containsInAnyOrder("BAR-151", "BAR-153", "BAR-157")))
                .andExpect(jsonPath("$.columns[1].tasks[?(@.task.key=='BAR-142')].git.prNumber").value(57))
                .andExpect(jsonPath("$.columns[1].tasks[?(@.task.key=='BAR-142')].git.checksStatus").value("PASSING"))
                .andExpect(jsonPath("$.columns[1].tasks[?(@.task.key=='BAR-142')].task.assigneeInitials").value("AR"))
                .andExpect(jsonPath("$.columns[2].tasks[?(@.task.key=='BAR-140')].git.checksStatus").value("FAILING"))
                .andExpect(jsonPath("$.columns[3].tasks[?(@.task.key=='BAR-131')].git.prState").value("MERGED"));
    }

    @Test
    void detailShowsSubtasksActivityAndGitLinks() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.task.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.task.reporterName").value("Omar Haddad"))
                .andExpect(jsonPath("$.task.sprintName").value("Sprint 23"))
                .andExpect(jsonPath("$.task.task.subtasksDone").value(2))
                .andExpect(jsonPath("$.task.task.subtasksTotal").value(4))
                .andExpect(jsonPath("$.task.subtasks", hasSize(4)))
                .andExpect(jsonPath("$.task.comments[0].author").value("Daniel Okoye"))
                .andExpect(jsonPath("$.task.activity[*].message", hasItem("opened pull request #57 in baracel/otp-service")))
                .andExpect(jsonPath("$.git.webhookLive").value(true))
                .andExpect(jsonPath("$.git.links[0].branch").value("feature/bar-142-whatsapp-otp"))
                .andExpect(jsonPath("$.git.links[0].prNumber").value(57))
                .andExpect(jsonPath("$.git.commits", hasSize(2)))
                .andExpect(jsonPath("$.git.commits[0].shortSha").value("a41c9e2"));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-999")).andExpect(status().isNotFound());
    }

    @Test
    void createTaskUsesThePerProjectSequence() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/tasks",
                        "{\"projectKey\":\"BAR\",\"title\":\"  New thing  \",\"priority\":\"HIGH\",\"label\":\"Backend\",\"dueDate\":\"2026-12-01\",\"assigneeId\":\""
                                + TestUsers.id("priya.nair") + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("BAR-158"))
                .andExpect(jsonPath("$.title").value("New thing"))
                .andExpect(jsonPath("$.status").value("BACKLOG"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.assigneeName").value("Priya Nair"));
        mvc.perform(postAs("aisha.rahman", "/api/tasks", "{\"projectKey\":\"BAR\",\"title\":\"Second\"}"))
                .andExpect(jsonPath("$.key").value("BAR-159"));
        // goes into the active sprint
        mvc.perform(getAs("aisha.rahman", "/api/projects/BAR/board")).andExpect(jsonPath("$.taskCount").value(13));
        mvc.perform(postAs("aisha.rahman", "/api/tasks", "{\"projectKey\":\"BAR\"}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("aisha.rahman", "/api/tasks", "{\"projectKey\":\"NOPE\",\"title\":\"x\"}")).andExpect(status().isNotFound());
        // assignee has to be a member of the project
        mvc.perform(postAs("aisha.rahman", "/api/tasks",
                        "{\"projectKey\":\"BAR\",\"title\":\"x\",\"assigneeId\":\"" + TestUsers.id("karim.aziz") + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/not-a-member")));
    }

    @Test
    void updateAndStatusChangesAreLoggedAsActivity() throws Exception {
        mvc.perform(patchAs("aisha.rahman", "/api/tasks/BAR-142/status", "{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142"))
                .andExpect(jsonPath("$.task.activity[0].message").value("moved this from In progress to In review"))
                .andExpect(jsonPath("$.task.activity[0].actor").value("Aisha Rahman"));
        mvc.perform(patchAs("aisha.rahman", "/api/tasks/BAR-142/status", "{\"status\":\"WRONG\"}")).andExpect(status().isBadRequest());
        mvc.perform(patchAs("aisha.rahman", "/api/tasks/BAR-142", "{\"title\":\"WhatsApp OTP\",\"priority\":\"URGENT\",\"dueDate\":\"2026-11-20\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("WhatsApp OTP")).andExpect(jsonPath("$.priority").value("URGENT"));
    }

    @Test
    void subtasksAndComments() throws Exception {
        String res = mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-142/subtasks", "{\"title\":\"Write runbook\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.done").value(false)).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(res, "$.id");
        mvc.perform(patchAs("aisha.rahman", "/api/tasks/BAR-142/subtasks/" + id, "{\"done\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.done").value(true));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142")).andExpect(jsonPath("$.task.task.subtasksDone").value(3))
                .andExpect(jsonPath("$.task.task.subtasksTotal").value(5));
        mvc.perform(deleteAs("aisha.rahman", "/api/tasks/BAR-142/subtasks/" + id)).andExpect(status().isNoContent());
        mvc.perform(deleteAs("aisha.rahman", "/api/tasks/BAR-142/subtasks/" + id)).andExpect(status().isNotFound());
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-142/subtasks", "{\"title\":\" \"}")).andExpect(status().isBadRequest());

        mvc.perform(postAs("omar.haddad", "/api/tasks/BAR-142/comments", "{\"body\":\"Looks good\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.author").value("Omar Haddad"));
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142"))
                .andExpect(jsonPath("$.task.comments", hasSize(2)))
                .andExpect(jsonPath("$.task.activity[0].message").value("commented: Looks good"));
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-142/comments", "{\"body\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void myTasksListWithDueFilter() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/tasks?assignee=me"))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].task.key").value("BAR-142"));
        mvc.perform(getAs("aisha.rahman", "/api/tasks?assignee=me&dueBefore=2026-11-17"))
                .andExpect(jsonPath("$[*].task.key", contains("BAR-142", "BAR-146")));
        mvc.perform(getAs("karim.aziz", "/api/tasks")).andExpect(jsonPath("$", hasSize(0)));
    }
}

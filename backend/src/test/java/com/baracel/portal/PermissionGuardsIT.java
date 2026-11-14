package com.baracel.portal;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Every endpoint that is protected by a permission answers 403 when the caller's roles do not carry it.
 * The matrix is edited through the admin API so the test also proves that matrix changes take effect.
 */
class PermissionGuardsIT extends IntegrationTest {
    private void setEmployeePermissions(String... keys) throws Exception {
        String json = "{\"permissions\":[" + String.join(",", java.util.Arrays.stream(keys).map(k -> "\"" + k + "\"").toList()) + "]}";
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/EMPLOYEE/matrix", json)).andExpect(status().isOk());
    }

    @Test
    void withoutLeaveRequestEveryLeaveEndpointIsForbidden() throws Exception {
        setEmployeePermissions("profile:read-own", "task:view");
        String u = "mei.tan";   // plain EMPLOYEE
        mvc.perform(getAs(u, "/api/leave/balances")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/leave/preview?type=ANNUAL&from=2026-12-07&to=2026-12-07")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/leave/requests", "{\"type\":\"ANNUAL\",\"from\":\"2026-12-07\",\"to\":\"2026-12-07\"}")).andExpect(status().isForbidden());
        mvc.perform(deleteAs(u, "/api/leave/requests/c0000000-0000-4000-8000-000000000001")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/leave/requests?scope=mine")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/leave/calendar?month=2026-11")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/employees?scope=directory")).andExpect(status().isForbidden());
    }

    @Test
    void withoutTaskPermissionsEveryTaskEndpointIsForbidden() throws Exception {
        setEmployeePermissions("profile:read-own", "leave:request");
        String u = "mei.tan";
        mvc.perform(getAs(u, "/api/projects")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/projects/BAR/board")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/tasks")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/tasks/BAR-142")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/tasks", "{\"projectKey\":\"BAR\",\"title\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(patchAs(u, "/api/tasks/BAR-142", "{\"title\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(patchAs(u, "/api/tasks/BAR-142/status", "{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/tasks/BAR-142/subtasks", "{\"title\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(patchAs(u, "/api/tasks/BAR-142/subtasks/1", "{\"done\":true}")).andExpect(status().isForbidden());
        mvc.perform(deleteAs(u, "/api/tasks/BAR-142/subtasks/1")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/tasks/BAR-142/comments", "{\"body\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/tasks/BAR-142/git/branch", "{\"repo\":\"baracel/portal\"}")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/tasks/BAR-142/git/link-pr", "{\"repo\":\"baracel/portal\",\"prNumber\":1}")).andExpect(status().isForbidden());
    }

    @Test
    void viewOnlyTaskRoleCannotChangeTasks() throws Exception {
        setEmployeePermissions("profile:read-own", "task:view");
        String u = "mei.tan";
        mvc.perform(getAs(u, "/api/projects/BAR/board")).andExpect(status().isOk());
        mvc.perform(getAs(u, "/api/tasks/BAR-149")).andExpect(status().isOk());
        mvc.perform(postAs(u, "/api/tasks", "{\"projectKey\":\"BAR\",\"title\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(patchAs(u, "/api/tasks/BAR-149/status", "{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/tasks/BAR-149/comments", "{\"body\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void withoutAiChatEveryAiEndpointIsForbidden() throws Exception {
        setEmployeePermissions("profile:read-own");
        String u = "aisha.rahman";   // SALES still has ai:chat? no: chat comes from EMPLOYEE, which was just reduced
        mvc.perform(getAs(u, "/api/ai/conversations")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/ai/conversations", "{}")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/ai/conversations/d0000000-0000-4000-8000-000000000001")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/ai/conversations/d0000000-0000-4000-8000-000000000001/messages", "{\"message\":\"hi\"}")).andExpect(status().isForbidden());
        mvc.perform(getAs(u, "/api/ai/access")).andExpect(status().isForbidden());
        mvc.perform(postAs(u, "/api/ai/drafts/f0000000-0000-4000-8000-000000000001/publish", "{\"channels\":[\"company-instagram\"]}")).andExpect(status().isForbidden());
    }

    @Test
    void repoViewAndHrPermissionsAreGuarded() throws Exception {
        mvc.perform(getAs("mei.tan", "/api/repos")).andExpect(status().isForbidden());
        for (String u : new String[]{"aisha.rahman", "omar.haddad", "priya.nair"}) {
            mvc.perform(postAs(u, "/api/announcements", "{\"title\":\"x\"}")).andExpect(status().isForbidden());
            mvc.perform(patchAs(u, "/api/employees/" + com.baracel.portal.support.TestUsers.id("mei.tan"), "{\"jobTitle\":\"x\"}")).andExpect(status().isForbidden());
            mvc.perform(postAs(u, "/api/employees/invite", "{\"email\":\"a@b.example\",\"firstName\":\"a\",\"lastName\":\"b\"}")).andExpect(status().isForbidden());
            mvc.perform(postAs(u, "/api/employees/" + com.baracel.portal.support.TestUsers.id("mei.tan") + "/approve", "{\"role\":\"SALES\"}")).andExpect(status().isForbidden());
        }
        for (String u : new String[]{"mei.tan", "sara.qureshi"}) {
            mvc.perform(getAs(u, "/api/employees?scope=team")).andExpect(status().isForbidden());
            mvc.perform(getAs(u, "/api/employees?scope=all")).andExpect(status().isForbidden());
            mvc.perform(getAs(u, "/api/leave/requests?scope=approvals")).andExpect(status().isForbidden());
            mvc.perform(postAs(u, "/api/leave/requests/c0000000-0000-4000-8000-000000000001/approve", "{}")).andExpect(status().isForbidden());
        }
    }
}

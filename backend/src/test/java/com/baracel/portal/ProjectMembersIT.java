package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ProjectMembersIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;

    private static String body(String user) { return "{\"employeeId\":\"" + TestUsers.id(user) + "\"}"; }

    @Test
    void membersSeeTheListAndOthersGetNotFound() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/projects/BAR/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[?(@.name=='Priya Nair')].jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$[?(@.name=='Priya Nair')].initials").value("PN"))
                .andExpect(jsonPath("$[0].id").exists());
        mvc.perform(getAs("karim.aziz", "/api/projects/BAR/members")).andExpect(status().isNotFound());   // not a member
        mvc.perform(getAs("aisha.rahman", "/api/projects/NOPE/members")).andExpect(status().isNotFound());
    }

    @Test
    void viewingNeedsTaskView() throws Exception {
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/EMPLOYEE/matrix", "{\"permissions\":[\"profile:read-own\"]}")).andExpect(status().isOk());
        mvc.perform(getAs("mei.tan", "/api/projects/BAR/members")).andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUsersCannotChangeMembers() throws Exception {
        for (String u : new String[]{"aisha.rahman", "priya.nair", "karim.aziz", "mei.tan"}) {
            mvc.perform(postAs(u, "/api/projects/BAR/members", body("karim.aziz"))).andExpect(status().isForbidden());
            mvc.perform(deleteAs(u, "/api/projects/BAR/members/" + TestUsers.id("mei.tan"))).andExpect(status().isForbidden());
        }
        mvc.perform(getAs("aisha.rahman", "/api/projects/BAR/members")).andExpect(jsonPath("$", hasSize(9)));
    }

    @Test
    void hrAndAdminCanAddAndRemoveAndEveryChangeIsAudited() throws Exception {
        mvc.perform(postAs("lena.fischer", "/api/projects/BAR/members", body("karim.aziz")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(10))).andExpect(jsonPath("$[*].name", hasItem("Karim Aziz")));
        mvc.perform(postAs("lena.fischer", "/api/projects/BAR/members", body("karim.aziz"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", endsWith("/already-a-member")));
        mvc.perform(postAs("daniel.okoye", "/api/projects/BAR/members", body("nadia.hussein"))).andExpect(status().isCreated());
        mvc.perform(deleteAs("lena.fischer", "/api/projects/BAR/members/" + TestUsers.id("nadia.hussein"))).andExpect(status().isNoContent());
        mvc.perform(deleteAs("lena.fischer", "/api/projects/BAR/members/" + TestUsers.id("nadia.hussein"))).andExpect(status().isNotFound());
        mvc.perform(postAs("lena.fischer", "/api/projects/BAR/members", "{\"employeeId\":\"00000000-0000-0000-0000-000000000000\"}")).andExpect(status().isNotFound());
        mvc.perform(postAs("lena.fischer", "/api/projects/BAR/members", "{}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("lena.fischer", "/api/projects/NOPE/members", body("karim.aziz"))).andExpect(status().isNotFound());
        Integer added = jdbc.queryForObject("select count(*) from audit_log where action='PROJECT_MEMBER_ADDED' and target_id='BAR'", Integer.class);
        Integer removed = jdbc.queryForObject("select count(*) from audit_log where action='PROJECT_MEMBER_REMOVED' and target_id='BAR'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(2, added);
        org.junit.jupiter.api.Assertions.assertEquals(1, removed);
    }

    @Test
    void aManagerInTheProjectCanManageItButNotOnceRemoved() throws Exception {
        mvc.perform(postAs("omar.haddad", "/api/projects/BAR/members", body("karim.aziz"))).andExpect(status().isCreated());
        mvc.perform(deleteAs("omar.haddad", "/api/projects/BAR/members/" + TestUsers.id("karim.aziz"))).andExpect(status().isNoContent());
        // HR removes Omar from the project: as a manager outside it he can no longer change members
        mvc.perform(deleteAs("lena.fischer", "/api/projects/BAR/members/" + TestUsers.id("omar.haddad"))).andExpect(status().isNoContent());
        mvc.perform(postAs("omar.haddad", "/api/projects/BAR/members", body("karim.aziz"))).andExpect(status().isForbidden());
        mvc.perform(deleteAs("omar.haddad", "/api/projects/BAR/members/" + TestUsers.id("mei.tan"))).andExpect(status().isForbidden());
    }

    @Test
    void assigneesMustStillBeMembers() throws Exception {
        String create = "{\"projectKey\":\"BAR\",\"title\":\"Needs Karim\",\"assigneeId\":\"" + TestUsers.id("karim.aziz") + "\"}";
        mvc.perform(postAs("aisha.rahman", "/api/tasks", create)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/not-a-member")));
        mvc.perform(patchAs("aisha.rahman", "/api/tasks/BAR-146", "{\"assigneeId\":\"" + TestUsers.id("karim.aziz") + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/not-a-member")));
        mvc.perform(postAs("lena.fischer", "/api/projects/BAR/members", body("karim.aziz"))).andExpect(status().isCreated());
        mvc.perform(postAs("aisha.rahman", "/api/tasks", create)).andExpect(status().isCreated());
        mvc.perform(deleteAs("lena.fischer", "/api/projects/BAR/members/" + TestUsers.id("karim.aziz"))).andExpect(status().isNoContent());
        mvc.perform(patchAs("aisha.rahman", "/api/tasks/BAR-146", "{\"assigneeId\":\"" + TestUsers.id("karim.aziz") + "\"}")).andExpect(status().isBadRequest());
    }
}

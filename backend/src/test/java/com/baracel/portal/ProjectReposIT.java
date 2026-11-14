package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.FakeGiteaClient;
import com.baracel.portal.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class ProjectReposIT extends IntegrationTest {

    @Test
    void membersListTheLinkedRepositoriesWithoutRepoView() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/repos")).andExpect(status().isForbidden());   // repo:view is unchanged
        mvc.perform(getAs("aisha.rahman", "/api/projects/BAR/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fullName").value("baracel/otp-service"))
                .andExpect(jsonPath("$[0].defaultBranch").value("main"))
                .andExpect(jsonPath("$[0].htmlUrl").value("http://localhost:3000/baracel/otp-service"))
                .andExpect(jsonPath("$[1].fullName").value("baracel/portal"));
    }

    @Test
    void nonMembersAndPeopleWithoutTaskViewAreRefused() throws Exception {
        mvc.perform(getAs("karim.aziz", "/api/projects/BAR/repos")).andExpect(status().isNotFound());
        mvc.perform(getAs("aisha.rahman", "/api/projects/NOPE/repos")).andExpect(status().isNotFound());
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/EMPLOYEE/matrix", "{\"permissions\":[\"profile:read-own\"]}")).andExpect(status().isOk());
        mvc.perform(getAs("mei.tan", "/api/projects/BAR/repos")).andExpect(status().isForbidden());
    }

    @Test
    void branchAndPrCallsAcceptOnlyRepositoriesLinkedToTheProject() throws Exception {
        for (String repo : new String[]{"baracel/other", "someone/else", "missing/nope"}) {
            mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/branch", "{\"repo\":\"" + repo + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/repo-not-linked")));
            mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/link-pr", "{\"repo\":\"" + repo + "\",\"prNumber\":5}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/repo-not-linked")));
        }
        org.junit.jupiter.api.Assertions.assertTrue(((FakeGiteaClient) gitea).branches.isEmpty());
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/branch", "{\"repo\":\"BARACEL/PORTAL\"}")).andExpect(status().isOk());   // case-insensitive match
        mvc.perform(postAs("aisha.rahman", "/api/tasks/BAR-146/git/branch", "{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", endsWith("/repo-required")));
    }
}

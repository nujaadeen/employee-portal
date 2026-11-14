package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.FakeKeycloakAdmin;
import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import org.junit.jupiter.api.Test;

class EmployeeIT extends IntegrationTest {
    static final String[] OMAR_REPORTS = {"Aisha Rahman", "Hana Saleh", "Joel Mathews", "Karim Aziz", "Nadia Hussein"};

    @Test
    void aishaCannotUseTeamOrAllScopes() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/employees?scope=team")).andExpect(status().isForbidden());
        mvc.perform(getAs("aisha.rahman", "/api/employees?scope=all")).andExpect(status().isForbidden());
        mvc.perform(getAs("aisha.rahman", "/api/employees?scope=directory")).andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/employees?scope=bogus")).andExpect(status().isBadRequest());
    }

    @Test
    void omarsTeamIsExactlyHisFiveReports() throws Exception {
        mvc.perform(getAs("omar.haddad", "/api/employees?scope=team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder(OMAR_REPORTS)))
                .andExpect(jsonPath("$.items[?(@.name=='Karim Aziz')].annualLeaveLeft").value(9))
                .andExpect(jsonPath("$.items[?(@.name=='Aisha Rahman')].annualLeaveLeft").value(14))
                .andExpect(jsonPath("$.items[?(@.name=='Hana Saleh')].status").value("ONBOARDING"))
                .andExpect(jsonPath("$.items[0].role").exists())
                .andExpect(jsonPath("$.items[0].joinedOn").exists());
    }

    @Test
    void callersCannotChooseAnotherManager() throws Exception {
        // a managerId parameter is ignored: the team always comes from the token
        mvc.perform(getAs("omar.haddad", "/api/employees?scope=team&managerId=" + TestUsers.id("ruth.mensah") + "&manager="
                        + TestUsers.id("ruth.mensah")))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder(OMAR_REPORTS)));
    }

    @Test
    void ruthsTeamNeverIncludesOmarsReports() throws Exception {
        mvc.perform(getAs("ruth.mensah", "/api/employees?scope=team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Omar Haddad", "Lena Fischer", "Sara Qureshi", "Daniel Okoye", "Mei Tan")))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Aisha Rahman"))))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Karim Aziz"))));
    }

    @Test
    void danielsTeamIsTheTwoEngineers() throws Exception {
        mvc.perform(getAs("daniel.okoye", "/api/employees?scope=team"))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Priya Nair", "Tom Becker")));
    }

    @Test
    void lenaSeesEveryoneWithFullRecords() throws Exception {
        mvc.perform(getAs("lena.fischer", "/api/employees?scope=all&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(13))
                .andExpect(jsonPath("$.items", hasSize(13)))
                .andExpect(jsonPath("$.items[?(@.name=='Aisha Rahman')].code").value("EMP-0142"))
                .andExpect(jsonPath("$.items[?(@.name=='Aisha Rahman')].managerName").value("Omar Haddad"))
                .andExpect(jsonPath("$.statusCounts.ALL").value(13))
                .andExpect(jsonPath("$.statusCounts.ON_LEAVE").value(3))
                .andExpect(jsonPath("$.statusCounts.ONBOARDING").value(2));
        mvc.perform(getAs("omar.haddad", "/api/employees?scope=all")).andExpect(status().isForbidden());
    }

    @Test
    void allScopeSupportsSearchAndFilters() throws Exception {
        mvc.perform(getAs("lena.fischer", "/api/employees?scope=all&q=aziz")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(getAs("lena.fischer", "/api/employees?scope=all&status=ON_LEAVE")).andExpect(jsonPath("$.total").value(3));
        mvc.perform(getAs("lena.fischer", "/api/employees?scope=all&department=Engineering")).andExpect(jsonPath("$.total").value(3));
        mvc.perform(getAs("lena.fischer", "/api/employees?scope=all&q=sales")).andExpect(jsonPath("$.total").value(6));
    }

    @Test
    void directoryShowsOnlyNameTitleDepartmentAndEmail() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/employees?scope=directory&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(13)))
                .andExpect(jsonPath("$.items[0].name").exists())
                .andExpect(jsonPath("$.items[0].jobTitle").exists())
                .andExpect(jsonPath("$.items[0].department").exists())
                .andExpect(jsonPath("$.items[0].email").exists())
                .andExpect(jsonPath("$.items[0].code").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.items[0].managerName").doesNotExist())
                .andExpect(jsonPath("$.items[0].annualLeaveLeft").doesNotExist());
    }

    @Test
    void singleRecordVisibility() throws Exception {
        String karim = TestUsers.id("karim.aziz").toString();
        mvc.perform(getAs("aisha.rahman", "/api/employees/" + TestUsers.id("aisha.rahman"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.code").value("EMP-0142"))
                .andExpect(jsonPath("$.roles").doesNotExist());
        mvc.perform(getAs("aisha.rahman", "/api/employees/" + karim)).andExpect(status().isForbidden());
        mvc.perform(getAs("omar.haddad", "/api/employees/" + karim)).andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.name").value("Karim Aziz"))
                .andExpect(jsonPath("$.recentLeave", hasSize(2)));
        mvc.perform(getAs("ruth.mensah", "/api/employees/" + karim)).andExpect(status().isForbidden());
        mvc.perform(getAs("lena.fischer", "/api/employees/" + karim)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("EMPLOYEE")))
                .andExpect(jsonPath("$.signInMethods", hasSize(4)));
        mvc.perform(getAs("lena.fischer", "/api/employees/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
    }

    @Test
    void onlyHrCanPatchRecords() throws Exception {
        String id = TestUsers.id("aisha.rahman").toString();
        mvc.perform(patchAs("aisha.rahman", "/api/employees/" + id, "{\"jobTitle\":\"Boss\"}")).andExpect(status().isForbidden());
        mvc.perform(patchAs("omar.haddad", "/api/employees/" + id, "{\"jobTitle\":\"Boss\"}")).andExpect(status().isForbidden());
        mvc.perform(patchAs("lena.fischer", "/api/employees/" + id, "{\"jobTitle\":\"Senior Sales Executive\",\"location\":\"Abu Dhabi\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobTitle").value("Senior Sales Executive"))
                .andExpect(jsonPath("$.location").value("Abu Dhabi"));
        mvc.perform(patchAs("lena.fischer", "/api/employees/" + id, "{\"managerId\":\"" + id + "\"}")).andExpect(status().isBadRequest());
        // reporting loop: Ruth (top) cannot report to Aisha, who is below her
        mvc.perform(patchAs("lena.fischer", "/api/employees/" + TestUsers.id("ruth.mensah"), "{\"managerId\":\"" + id + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hrCanInviteAndKeycloakGetsTheUser() throws Exception {
        String body = "{\"email\":\"new.hire@baracel.example\",\"firstName\":\"New\",\"lastName\":\"Hire\",\"jobTitle\":\"Analyst\",\"department\":\"Finance\",\"role\":\"EMPLOYEE\"}";
        mvc.perform(postAs("aisha.rahman", "/api/employees/invite", body)).andExpect(status().isForbidden());
        mvc.perform(postAs("lena.fischer", "/api/employees/invite", body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ONBOARDING"))
                .andExpect(jsonPath("$.code", matchesPattern("EMP-\\d{4}")));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("new.hire@baracel.example"), ((FakeKeycloakAdmin) keycloak).createdEmails);
        mvc.perform(postAs("lena.fischer", "/api/employees/invite", body)).andExpect(status().isConflict());
        mvc.perform(postAs("lena.fischer", "/api/employees/invite", "{\"email\":\"not-an-email\",\"firstName\":\"a\",\"lastName\":\"b\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvalSetsRoleManagerAndStatusAndUpdatesKeycloak() throws Exception {
        String token = custom("kc-approve-1", "fresh@baracel.example", "Fresh", "Face");
        String id = com.jayway.jsonpath.JsonPath.read(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me")
                .header("Authorization", token)).andReturn().getResponse().getContentAsString(), "$.id");
        String body = "{\"role\":\"SALES\",\"managerId\":\"" + TestUsers.id("omar.haddad") + "\",\"jobTitle\":\"Sales Executive\",\"department\":\"Sales\"}";
        mvc.perform(postAs("aisha.rahman", "/api/employees/" + id + "/approve", body)).andExpect(status().isForbidden());
        mvc.perform(postAs("lena.fischer", "/api/employees/" + id + "/approve", body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("SALES"))
                .andExpect(jsonPath("$.managerName").value("Omar Haddad"));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("EMPLOYEE", "SALES"), ((FakeKeycloakAdmin) keycloak).rolesSet.get("kc-approve-1"));
        // the approved person can now use the portal
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard").header("Authorization", token))
                .andExpect(status().isOk());
        // second approval is a conflict
        mvc.perform(postAs("lena.fischer", "/api/employees/" + id + "/approve", body)).andExpect(status().isConflict());
    }

    @Test
    void onlyAdminMayAssignHrOrAdmin() throws Exception {
        String token = custom("kc-approve-2", "another@baracel.example", "An", "Other");
        String id = com.jayway.jsonpath.JsonPath.read(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me")
                .header("Authorization", token)).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(postAs("lena.fischer", "/api/employees/" + id + "/approve", "{\"role\":\"HR\"}")).andExpect(status().isForbidden());
        mvc.perform(postAs("lena.fischer", "/api/employees/" + id + "/approve", "{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
        mvc.perform(postAs("lena.fischer", "/api/employees/" + id + "/approve", "{\"role\":\"NOPE\"}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("daniel.okoye", "/api/employees/" + id + "/approve", "{\"role\":\"HR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("HR"));
        // inviting an administrator is also an admin-only act
        mvc.perform(postAs("lena.fischer", "/api/employees/invite", "{\"email\":\"a@b.example\",\"firstName\":\"A\",\"lastName\":\"B\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }
}

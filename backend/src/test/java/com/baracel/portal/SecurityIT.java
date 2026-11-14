package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class SecurityIT extends IntegrationTest {

    @Test
    void healthIsOpenButEverythingElseNeedsAToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", endsWith("/unauthorized")));
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsProfileRolesPermissionsAndSignInMethods() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("b0000000-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$.code").value("EMP-0142"))
                .andExpect(jsonPath("$.name").value("Aisha Rahman"))
                .andExpect(jsonPath("$.jobTitle").value("Sales Executive"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.manager.name").value("Omar Haddad"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("EMPLOYEE", "SALES")))
                .andExpect(jsonPath("$.permissions", hasItems("leave:request", "ai:knowledge:product", "social:publish:company")))
                .andExpect(jsonPath("$.permissions", not(hasItems("leave:approve", "employee:team", "employee:records", "access:manage"))))
                .andExpect(jsonPath("$.signInMethods[?(@.key=='google')].linked").value(true))
                .andExpect(jsonPath("$.unreadNotifications").value(0));
    }

    @Test
    void permissionsAreTheUnionOfAllRoles() throws Exception {
        // Daniel is ADMIN + ENGINEER + MANAGER
        mvc.perform(getAs("daniel.okoye", "/api/me"))
                .andExpect(jsonPath("$.permissions", hasItems("access:manage", "repo:view", "leave:approve", "employee:team", "ai:knowledge:hr")));
        mvc.perform(getAs("omar.haddad", "/api/me"))
                .andExpect(jsonPath("$.permissions", hasItems("leave:approve", "employee:team")))
                .andExpect(jsonPath("$.permissions", not(hasItem("employee:records"))));
        mvc.perform(getAs("omar.haddad", "/api/me")).andExpect(jsonPath("$.unreadNotifications").value(2));
    }

    @Test
    void firstCallOfAnUnknownSubCreatesAPendingEmployee() throws Exception {
        String token = custom("kc-new-1", "new.person@baracel.example", "New", "Person");
        mvc.perform(get("/api/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.email").value("new.person@baracel.example"))
                .andExpect(jsonPath("$.name").value("New Person"))
                .andExpect(jsonPath("$.code", matchesPattern("EMP-\\d{4}")));
        // calling again must not create a second row: same code
        String code1 = com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString(), "$.code");
        String code2 = com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString(), "$.code");
        org.junit.jupiter.api.Assertions.assertEquals(code1, code2);
    }

    @Test
    void pendingApprovalUserCanOnlyCallMe() throws Exception {
        String token = custom("kc-new-2", "pending@baracel.example", "Pen", "Ding");
        mvc.perform(get("/api/me").header("Authorization", token)).andExpect(status().isOk());
        for (String url : new String[]{"/api/dashboard", "/api/leave/balances", "/api/employees?scope=directory", "/api/projects",
                "/api/notifications", "/api/ai/access", "/api/announcements"}) {
            mvc.perform(get(url).header("Authorization", token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type", endsWith("/pending-approval")));
        }
    }
}

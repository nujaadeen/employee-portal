package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.FakeKeycloakAdmin;
import com.baracel.portal.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AccessIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyAdminCanUseTheAccessEndpoints() throws Exception {
        for (String user : new String[]{"aisha.rahman", "omar.haddad", "lena.fischer", "priya.nair"}) {
            mvc.perform(getAs(user, "/api/access/roles")).andExpect(status().isForbidden());
            mvc.perform(getAs(user, "/api/access/roles/SALES/matrix")).andExpect(status().isForbidden());
            mvc.perform(putAs(user, "/api/access/roles/SALES/matrix", "{\"permissions\":[]}")).andExpect(status().isForbidden());
            mvc.perform(getAs(user, "/api/access/identity-providers")).andExpect(status().isForbidden());
            mvc.perform(putAs(user, "/api/access/identity-providers", "{\"providers\":[]}")).andExpect(status().isForbidden());
            mvc.perform(getAs(user, "/api/access/settings")).andExpect(status().isForbidden());
        }
    }

    @Test
    void rolesListHasUserCountsFromKeycloak() throws Exception {
        mvc.perform(getAs("daniel.okoye", "/api/access/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("EMPLOYEE", "MANAGER", "HR", "SALES", "ENGINEER", "ADMIN")))
                .andExpect(jsonPath("$[0].people").value(13))
                .andExpect(jsonPath("$[3].people").value(3));
    }

    @Test
    void salesMatrixMatchesTheScreen() throws Exception {
        mvc.perform(getAs("daniel.okoye", "/api/access/roles/SALES/matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SALES"))
                .andExpect(jsonPath("$.permissions[?(@.key=='ai:knowledge:product')].granted").value(true))
                .andExpect(jsonPath("$.permissions[?(@.key=='ai:knowledge:brand')].granted").value(true))
                .andExpect(jsonPath("$.permissions[?(@.key=='ai:knowledge:campaign')].granted").value(true))
                .andExpect(jsonPath("$.permissions[?(@.key=='ai:knowledge:engineering')].granted").value(false))
                .andExpect(jsonPath("$.permissions[?(@.key=='social:publish:company')].granted").value(true))
                .andExpect(jsonPath("$.permissions[?(@.key=='leave:approve')].granted").value(false))
                .andExpect(jsonPath("$.permissions[?(@.key=='repo:view')].granted").value(false));
        mvc.perform(getAs("daniel.okoye", "/api/access/roles/NOPE/matrix")).andExpect(status().isNotFound());
    }

    @Test
    void matrixUpdateIsAuditedAndTakesEffectOnTheNextRequest() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/me")).andExpect(jsonPath("$.permissions", hasItem("social:publish:personal")));
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/SALES/matrix",
                        "{\"permissions\":[\"ai:knowledge:product\",\"ai:knowledge:brand\",\"ai:knowledge:campaign\",\"social:publish:company\",\"repo:view\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@.key=='social:publish:personal')].granted").value(false))
                .andExpect(jsonPath("$.permissions[?(@.key=='repo:view')].granted").value(true));
        // the permission cache was cleared: Aisha sees the new set without a restart
        mvc.perform(getAs("aisha.rahman", "/api/me"))
                .andExpect(jsonPath("$.permissions", not(hasItem("social:publish:personal"))))
                .andExpect(jsonPath("$.permissions", hasItem("repo:view")));
        Integer audits = jdbc.queryForObject("select count(*) from audit_log where action='ACCESS_MATRIX_UPDATED' and target_id='SALES'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, audits);
    }

    @Test
    void invalidMatrixUpdatesAreRejected() throws Exception {
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/SALES/matrix", "{\"permissions\":[\"made:up\"]}")).andExpect(status().isBadRequest());
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/ADMIN/matrix", "{\"permissions\":[\"task:view\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/lockout")));
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/SALES/matrix", "{}")).andExpect(status().isBadRequest());
    }

    @Test
    void identityProvidersCanBeReadAndSwitchedThroughKeycloak() throws Exception {
        mvc.perform(getAs("daniel.okoye", "/api/access/identity-providers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.alias=='github')].enabled").value(true));
        mvc.perform(putAs("daniel.okoye", "/api/access/identity-providers", "{\"providers\":[{\"alias\":\"github\",\"enabled\":false}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.alias=='github')].enabled").value(false));
        org.junit.jupiter.api.Assertions.assertFalse(((FakeKeycloakAdmin) keycloak).idpEnabled.get("github"));
        mvc.perform(putAs("daniel.okoye", "/api/access/identity-providers", "{\"providers\":[{\"alias\":\"password\",\"enabled\":false}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settingsAreReadOnlyInfo() throws Exception {
        mvc.perform(getAs("daniel.okoye", "/api/access/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultRoleForNewSignUps").value("EMPLOYEE (pending)"))
                .andExpect(jsonPath("$.approver").value("HR"))
                .andExpect(jsonPath("$.sessionLengthHours").value(8));
        mvc.perform(putAs("daniel.okoye", "/api/access/settings", "{}")).andExpect(status().isMethodNotAllowed());
    }
}

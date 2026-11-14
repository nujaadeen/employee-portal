package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.FakeKeycloakAdmin;
import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RoleManagementIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;

    private static final String NEW_ROLE = "{\"name\":\"CONTRACTOR\",\"description\":\"External contractors\"}";

    @Test
    void onlyAdminCanCreateOrDeleteRoles() throws Exception {
        for (String u : new String[]{"aisha.rahman", "omar.haddad", "lena.fischer", "priya.nair"}) {
            mvc.perform(postAs(u, "/api/access/roles", NEW_ROLE)).andExpect(status().isForbidden());
            mvc.perform(deleteAs(u, "/api/access/roles/CONTRACTOR")).andExpect(status().isForbidden());
        }
        org.junit.jupiter.api.Assertions.assertFalse(((FakeKeycloakAdmin) keycloak).roles.contains("CONTRACTOR"));
    }

    @Test
    void adminCreatesARoleWithAnEmptyPermissionSet() throws Exception {
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", NEW_ROLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CONTRACTOR"))
                .andExpect(jsonPath("$.description").value("External contractors"))
                .andExpect(jsonPath("$.builtIn").value(false))
                .andExpect(jsonPath("$.people").value(0));
        org.junit.jupiter.api.Assertions.assertTrue(((FakeKeycloakAdmin) keycloak).roles.contains("CONTRACTOR"));
        mvc.perform(getAs("daniel.okoye", "/api/access/roles"))
                .andExpect(jsonPath("$[*].name", contains("EMPLOYEE", "MANAGER", "HR", "SALES", "ENGINEER", "ADMIN", "CONTRACTOR")))
                .andExpect(jsonPath("$[6].builtIn").value(false))
                .andExpect(jsonPath("$[0].builtIn").value(true));
        mvc.perform(getAs("daniel.okoye", "/api/access/roles/CONTRACTOR/matrix"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions[?(@.granted==true)]", hasSize(0)));
        // the matrix of a custom role can be edited like any other
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/CONTRACTOR/matrix", "{\"permissions\":[\"task:view\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions[?(@.key=='task:view')].granted").value(true));
        Integer audits = jdbc.queryForObject("select count(*) from audit_log where action='ROLE_CREATED' and target_id='CONTRACTOR'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, audits);
    }

    @Test
    void namesAreValidatedAndMustBeNew() throws Exception {
        for (String bad : new String[]{"contractor", "AB", "A".repeat(31), "CONTRACTOR1", "CON TRACTOR", "CON-TRACTOR", ""})
            mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/invalid-role-name")));
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"ABC\"}")).andExpect(status().isCreated());   // 3 characters
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"" + "A".repeat(30) + "\"}")).andExpect(status().isCreated());   // 30 characters
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"MY_ROLE\"}")).andExpect(status().isCreated());   // underscore allowed
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"ABC\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.type", endsWith("/role-exists")));
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"SALES\"}")).andExpect(status().isConflict());
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"ADMIN\"}")).andExpect(status().isConflict());
    }

    @Test
    void aRoleThatExistsOnlyInKeycloakIsAlsoTaken() throws Exception {
        ((FakeKeycloakAdmin) keycloak).roles.add("LEGACY_ROLE");
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", "{\"name\":\"LEGACY_ROLE\"}")).andExpect(status().isConflict());
    }

    @Test
    void onlyCustomRolesWithNoUsersCanBeDeleted() throws Exception {
        for (String builtIn : new String[]{"EMPLOYEE", "MANAGER", "HR", "SALES", "ENGINEER", "ADMIN"})
            mvc.perform(deleteAs("daniel.okoye", "/api/access/roles/" + builtIn))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/built-in-role")));
        mvc.perform(deleteAs("daniel.okoye", "/api/access/roles/NOT_THERE")).andExpect(status().isNotFound());

        mvc.perform(postAs("daniel.okoye", "/api/access/roles", NEW_ROLE)).andExpect(status().isCreated());
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/CONTRACTOR/matrix", "{\"permissions\":[\"task:view\"]}")).andExpect(status().isOk());
        ((FakeKeycloakAdmin) keycloak).roleUsers.put("CONTRACTOR", 2);
        mvc.perform(deleteAs("daniel.okoye", "/api/access/roles/CONTRACTOR")).andExpect(status().isConflict()).andExpect(jsonPath("$.type", endsWith("/role-in-use")));
        org.junit.jupiter.api.Assertions.assertTrue(((FakeKeycloakAdmin) keycloak).roles.contains("CONTRACTOR"));

        ((FakeKeycloakAdmin) keycloak).roleUsers.put("CONTRACTOR", 0);
        mvc.perform(deleteAs("daniel.okoye", "/api/access/roles/CONTRACTOR")).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(((FakeKeycloakAdmin) keycloak).roles.contains("CONTRACTOR"));
        mvc.perform(getAs("daniel.okoye", "/api/access/roles/CONTRACTOR/matrix")).andExpect(status().isNotFound());
        mvc.perform(getAs("daniel.okoye", "/api/access/roles")).andExpect(jsonPath("$[*].name", not(hasItem("CONTRACTOR"))));
        Integer left = jdbc.queryForObject("select count(*) from role_permission where role_name='CONTRACTOR'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, left);
        Integer audits = jdbc.queryForObject("select count(*) from audit_log where action='ROLE_DELETED' and target_id='CONTRACTOR'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, audits);
    }

    @Test
    void aCustomRoleGivesItsPermissionsToItsHolders() throws Exception {
        mvc.perform(postAs("daniel.okoye", "/api/access/roles", NEW_ROLE)).andExpect(status().isCreated());
        mvc.perform(putAs("daniel.okoye", "/api/access/roles/CONTRACTOR/matrix", "{\"permissions\":[\"repo:view\"]}")).andExpect(status().isOk());
        String token = "Bearer custom~" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("kc-contractor:c@baracel.example:Con:Tractor:EMPLOYEE,CONTRACTOR".getBytes());
        // a brand new sign-in is pending, but the converter already maps the custom role to its permissions
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions", hasItem("repo:view")));
        org.junit.jupiter.api.Assertions.assertNotNull(TestUsers.id("aisha.rahman"));
    }
}

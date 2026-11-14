package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.FakeKeycloakAdmin;
import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ResetAccessIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;

    private FakeKeycloakAdmin kc() { return (FakeKeycloakAdmin) keycloak; }

    @Test
    void hrSendsTheResetEmailAndNothingSecretComesBack() throws Exception {
        String aisha = TestUsers.id("aisha.rahman").toString();
        String body = mvc.perform(postAs("lena.fischer", "/api/employees/" + aisha + "/reset-access", "{}"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals("", body);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(aisha + ":UPDATE_PASSWORD,VERIFY_EMAIL"), kc().actionEmails);
        String detail = jdbc.queryForObject("select detail from audit_log where action='EMPLOYEE_ACCESS_RESET' and target_id='EMP-0142'", String.class);
        org.junit.jupiter.api.Assertions.assertFalse(detail.toLowerCase().contains("password:"));
        Integer rows = jdbc.queryForObject("select count(*) from audit_log where action='EMPLOYEE_ACCESS_RESET' and actor_name='Lena Fischer'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, rows);
    }

    @Test
    void everyoneWithoutEmployeeManageIsRefused() throws Exception {
        for (String u : new String[]{"aisha.rahman", "omar.haddad", "priya.nair", "karim.aziz"}) {
            mvc.perform(postAs(u, "/api/employees/" + TestUsers.id("mei.tan") + "/reset-access", "{}")).andExpect(status().isForbidden());
            // not even for themselves
            mvc.perform(postAs(u, "/api/employees/" + TestUsers.id(u) + "/reset-access", "{}")).andExpect(status().isForbidden());
        }
        org.junit.jupiter.api.Assertions.assertTrue(kc().actionEmails.isEmpty());
    }

    @Test
    void hrCannotResetAnAdministratorButAnAdministratorCan() throws Exception {
        String daniel = TestUsers.id("daniel.okoye").toString();
        kc().rolesSet.put(daniel, Set.of("EMPLOYEE", "ADMIN"));
        mvc.perform(postAs("lena.fischer", "/api/employees/" + daniel + "/reset-access", "{}")).andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertTrue(kc().actionEmails.isEmpty());
        // HR can still reset a manager, and an administrator can reset another administrator
        mvc.perform(postAs("lena.fischer", "/api/employees/" + TestUsers.id("omar.haddad") + "/reset-access", "{}")).andExpect(status().isNoContent());
        String lena = TestUsers.id("lena.fischer").toString();
        kc().rolesSet.put(lena, Set.of("EMPLOYEE", "ADMIN"));
        mvc.perform(postAs("daniel.okoye", "/api/employees/" + lena + "/reset-access", "{}")).andExpect(status().isNoContent());
    }

    @Test
    void unknownEmployeeIsNotFound() throws Exception {
        mvc.perform(postAs("lena.fischer", "/api/employees/00000000-0000-0000-0000-000000000000/reset-access", "{}")).andExpect(status().isNotFound());
    }
}

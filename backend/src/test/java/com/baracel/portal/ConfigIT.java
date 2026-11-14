package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class ConfigIT extends IntegrationTest {
    @Test
    void anySignedInPersonGetsThePublicConfigWithoutSecrets() throws Exception {
        for (String u : new String[]{"aisha.rahman", "omar.haddad", "lena.fischer", "sara.qureshi"}) {
            mvc.perform(getAs(u, "/api/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.appName").value("Baracel"))
                    .andExpect(jsonPath("$.giteaUrl").value("http://localhost:3000"))
                    .andExpect(jsonPath("$.leaveTypes[*].code", contains("ANNUAL", "SICK", "COMP_OFF", "PARENTAL", "UNPAID")))
                    .andExpect(jsonPath("$.leaveTypes[0].name").value("Annual"))
                    .andExpect(jsonPath("$.leaveTypes[0].defaultDays").value(30));
        }
        String body = mvc.perform(getAs("aisha.rahman", "/api/config")).andReturn().getResponse().getContentAsString().toLowerCase();
        for (String secret : new String[]{"token", "secret", "password", "test-secret", "test-token"})
            org.junit.jupiter.api.Assertions.assertFalse(body.contains(secret), "config leaks " + secret);
    }

    @Test
    void anonymousAndPendingApprovalAreRefused() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/config").header("Authorization", custom("kc-cfg-1", "cfg@baracel.example", "Cfg", "User")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.type", endsWith("/pending-approval")));
    }
}

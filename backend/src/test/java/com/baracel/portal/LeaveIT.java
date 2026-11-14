package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.leave.service.LeaveService;
import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class LeaveIT extends IntegrationTest {
    static final String KARIM_PENDING = "c0000000-0000-4000-8000-000000000001";
    static final String JOEL_PENDING = "c0000000-0000-4000-8000-000000000002";

    @Autowired JdbcTemplate jdbc;
    @Autowired LeaveService leaveService;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

    String create(String user, String type, String from, String to) throws Exception {
        String res = mvc.perform(postAs(user, "/api/leave/requests",
                        "{\"type\":\"" + type + "\",\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"reason\":\"test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    @Test
    void balancesMatchTheScreen() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/leave/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='ANNUAL')].left").value(14))
                .andExpect(jsonPath("$[?(@.type=='ANNUAL')].entitled").value(30))
                .andExpect(jsonPath("$[?(@.type=='SICK')].left").value(8))
                .andExpect(jsonPath("$[?(@.type=='COMP_OFF')].left").value(2))
                .andExpect(jsonPath("$[?(@.type=='PARENTAL')].left").value(5))
                .andExpect(jsonPath("$[4].name").value("Unpaid"))
                .andExpect(jsonPath("$[4].entitled").doesNotExist());
    }

    @Test
    void aishaCannotApproveOrSeeApprovals() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests/" + KARIM_PENDING + "/approve", "{}")).andExpect(status().isForbidden());
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests/" + KARIM_PENDING + "/reject", "{}")).andExpect(status().isForbidden());
        mvc.perform(getAs("aisha.rahman", "/api/leave/requests?scope=approvals")).andExpect(status().isForbidden());
    }

    @Test
    void managerSeesOnlyPendingRequestsOfDirectReportsAndHrSeesAll() throws Exception {
        mvc.perform(getAs("omar.haddad", "/api/leave/requests?scope=approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].employeeName", containsInAnyOrder("Karim Aziz", "Joel Mathews")))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(getAs("omar.haddad", "/api/leave/requests?scope=approvals&status=ALL"))
                .andExpect(jsonPath("$[*].employeeName", hasItems("Nadia Hussein", "Karim Aziz", "Joel Mathews", "Hana Saleh")))
                .andExpect(jsonPath("$[*].employeeName", not(hasItem("Tom Becker"))));
        mvc.perform(getAs("ruth.mensah", "/api/leave/requests?scope=approvals")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(getAs("lena.fischer", "/api/leave/requests?scope=approvals&status=ALL"))
                .andExpect(jsonPath("$[*].employeeName", hasItems("Karim Aziz", "Tom Becker", "Priya Nair")));
    }

    @Test
    void managerApprovesADirectReportAndTheBalanceIsUsed() throws Exception {
        mvc.perform(postAs("omar.haddad", "/api/leave/requests/" + KARIM_PENDING + "/approve", "{\"note\":\"Enjoy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("Omar Haddad"))
                .andExpect(jsonPath("$.note").value("Enjoy"));
        mvc.perform(getAs("karim.aziz", "/api/leave/balances")).andExpect(jsonPath("$[?(@.type=='ANNUAL')].left").value(4));
        mvc.perform(postAs("omar.haddad", "/api/leave/requests/" + KARIM_PENDING + "/approve", "{}")).andExpect(status().isConflict());
        mvc.perform(getAs("karim.aziz", "/api/notifications")).andExpect(jsonPath("$.items[0].title", containsString("approved")));
    }

    @Test
    void rejectingNotifiesTheEmployeeAndKeepsTheBalance() throws Exception {
        mvc.perform(postAs("omar.haddad", "/api/leave/requests/" + JOEL_PENDING + "/reject", "{\"note\":\"Release week\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(getAs("joel.mathews", "/api/leave/balances")).andExpect(jsonPath("$[?(@.type=='ANNUAL')].left").value(17));
        mvc.perform(getAs("joel.mathews", "/api/notifications")).andExpect(jsonPath("$.items[0].title", containsString("rejected")))
                .andExpect(jsonPath("$.items[0].body", containsString("Release week")));
    }

    @Test
    void aManagerCannotApproveLeaveOfSomeoneWhoDoesNotReportToThem() throws Exception {
        String priyaRequest = create("priya.nair", "ANNUAL", "2026-12-07", "2026-12-08");
        mvc.perform(postAs("omar.haddad", "/api/leave/requests/" + priyaRequest + "/approve", "{}")).andExpect(status().isForbidden());
        mvc.perform(postAs("omar.haddad", "/api/leave/requests/" + priyaRequest + "/reject", "{}")).andExpect(status().isForbidden());
        // Ruth manages Omar, not Karim
        mvc.perform(postAs("ruth.mensah", "/api/leave/requests/" + KARIM_PENDING + "/approve", "{}")).andExpect(status().isForbidden());
        // Daniel (Priya's manager) can
        mvc.perform(postAs("daniel.okoye", "/api/leave/requests/" + priyaRequest + "/approve", "{}")).andExpect(status().isOk());
    }

    @Test
    void hrCanApproveAnyoneButNobodyApprovesTheirOwnRequest() throws Exception {
        mvc.perform(postAs("lena.fischer", "/api/leave/requests/" + KARIM_PENDING + "/approve", "{}")).andExpect(status().isOk());
        String omars = create("omar.haddad", "ANNUAL", "2026-12-14", "2026-12-15");
        mvc.perform(postAs("omar.haddad", "/api/leave/requests/" + omars + "/approve", "{}")).andExpect(status().isForbidden());
        mvc.perform(postAs("lena.fischer", "/api/leave/requests/" + omars + "/approve", "{}")).andExpect(status().isOk());
        String lenas = create("lena.fischer", "ANNUAL", "2026-12-21", "2026-12-21");
        mvc.perform(postAs("lena.fischer", "/api/leave/requests/" + lenas + "/approve", "{}")).andExpect(status().isForbidden());
        mvc.perform(postAs("daniel.okoye", "/api/leave/requests/" + lenas + "/approve", "{}")).andExpect(status().isOk());
    }

    @Test
    void submittingNotifiesTheManager() throws Exception {
        create("aisha.rahman", "ANNUAL", "2026-12-07", "2026-12-07");
        mvc.perform(getAs("omar.haddad", "/api/notifications"))
                .andExpect(jsonPath("$.items[0].title").value("Leave request from Aisha Rahman"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(getAs("omar.haddad", "/api/me")).andExpect(jsonPath("$.unreadNotifications").value(3));
        Integer id = JsonPath.read(mvc.perform(getAs("omar.haddad", "/api/notifications")).andReturn().getResponse().getContentAsString(), "$.items[0].id");
        mvc.perform(postAs("omar.haddad", "/api/notifications/" + id + "/read", "{}")).andExpect(jsonPath("$.read").value(true));
        mvc.perform(getAs("omar.haddad", "/api/me")).andExpect(jsonPath("$.unreadNotifications").value(2));
        mvc.perform(postAs("aisha.rahman", "/api/notifications/" + id + "/read", "{}")).andExpect(status().isNotFound());
    }

    @Test
    void requestValidation() throws Exception {
        // end before start
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests", "{\"type\":\"ANNUAL\",\"from\":\"2026-12-10\",\"to\":\"2026-12-09\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/invalid-dates")));
        // weekend only (Sat 21 and Sun 22 Nov 2026)
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests", "{\"type\":\"ANNUAL\",\"from\":\"2026-11-21\",\"to\":\"2026-11-22\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type", endsWith("/no-working-days")));
        // unknown type, missing fields
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests", "{\"type\":\"HOLIDAY\",\"from\":\"2026-12-10\",\"to\":\"2026-12-10\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests", "{\"type\":\"ANNUAL\"}")).andExpect(status().isBadRequest());
        // overlap with own approved request: Nadia is away 13 to 16 Nov
        mvc.perform(postAs("nadia.hussein", "/api/leave/requests", "{\"type\":\"ANNUAL\",\"from\":\"2026-11-16\",\"to\":\"2026-11-17\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type", endsWith("/leave-overlap")));
        // not enough comp-off: Aisha has 2 days left, 3 working days requested
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests", "{\"type\":\"COMP_OFF\",\"from\":\"2026-12-07\",\"to\":\"2026-12-09\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type", endsWith("/insufficient-balance")));
    }

    @Test
    void pendingRequestsAreReservedAgainstTheBalance() throws Exception {
        create("aisha.rahman", "COMP_OFF", "2026-12-07", "2026-12-08");   // uses the 2 days that are left
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests", "{\"type\":\"COMP_OFF\",\"from\":\"2026-12-10\",\"to\":\"2026-12-10\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void workingDaysSkipWeekendsAndPublicHolidays() throws Exception {
        // Tue 1 Dec to Fri 4 Dec 2026; 2 and 3 December are public holidays
        mvc.perform(postAs("aisha.rahman", "/api/leave/requests", "{\"type\":\"ANNUAL\",\"from\":\"2026-12-01\",\"to\":\"2026-12-04\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.days").value(2));
        // Fri 4 Dec to Mon 7 Dec: the weekend is skipped
        mvc.perform(postAs("joel.mathews", "/api/leave/requests", "{\"type\":\"ANNUAL\",\"from\":\"2026-12-04\",\"to\":\"2026-12-07\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.days").value(2));
    }

    @Test
    void previewShowsDaysBalanceAfterAndOverlaps() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/leave/preview?type=ANNUAL&from=2026-11-19&to=2026-11-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(1))
                .andExpect(jsonPath("$.balanceAfter").value(13))
                .andExpect(jsonPath("$.overlaps", empty()));
        mvc.perform(getAs("aisha.rahman", "/api/leave/preview?type=ANNUAL&from=2026-11-23&to=2026-11-23"))
                .andExpect(jsonPath("$.overlaps", hasItem("Karim Aziz")));
    }

    @Test
    void employeeCancelsOwnPendingRequestOnly() throws Exception {
        String id = create("aisha.rahman", "ANNUAL", "2026-12-07", "2026-12-07");
        mvc.perform(deleteAs("karim.aziz", "/api/leave/requests/" + id)).andExpect(status().isNotFound());
        mvc.perform(deleteAs("aisha.rahman", "/api/leave/requests/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(deleteAs("aisha.rahman", "/api/leave/requests/" + id)).andExpect(status().isConflict());
        // an approved request cannot be cancelled this way
        mvc.perform(deleteAs("nadia.hussein", "/api/leave/requests/c0000000-0000-4000-8000-000000000003")).andExpect(status().isConflict());
        mvc.perform(getAs("aisha.rahman", "/api/leave/requests?scope=mine")).andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void calendarShowsTheCallersDepartmentOnly() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/leave/calendar?month=2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Karim Aziz", "Joel Mathews", "Nadia Hussein", "Hana Saleh")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Tom Becker"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Priya Nair"))));
        mvc.perform(getAs("priya.nair", "/api/leave/calendar?month=2026-11")).andExpect(jsonPath("$[*].name", hasItems("Tom Becker", "Priya Nair")));
    }

    @Test
    void dailyJobSetsOnLeaveAndBackToActive() {
        jdbc.update("update employee set status='ACTIVE' where id = ?::uuid", TestUsers.id("tom.becker").toString());      // has leave today
        jdbc.update("update employee set status='ON_LEAVE' where id = ?::uuid", TestUsers.id("karim.aziz").toString());    // has none
        jdbc.update("update employee set status='ONBOARDING' where id = ?::uuid", TestUsers.id("nadia.hussein").toString()); // untouched
        org.junit.jupiter.api.Assertions.assertEquals(2, leaveService.syncEmployeeStatuses());
        em.flush();
        org.junit.jupiter.api.Assertions.assertEquals("ON_LEAVE", statusOf("tom.becker"));
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", statusOf("karim.aziz"));
        org.junit.jupiter.api.Assertions.assertEquals("ONBOARDING", statusOf("nadia.hussein"));
    }

    private String statusOf(String user) {
        return jdbc.queryForObject("select status from employee where id = ?::uuid", String.class, TestUsers.id(user).toString());
    }
}

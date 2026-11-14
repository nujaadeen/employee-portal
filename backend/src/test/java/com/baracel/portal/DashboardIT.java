package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class DashboardIT extends IntegrationTest {

    @Test
    void dashboardForAisha() throws Exception {
        mvc.perform(getAs("aisha.rahman", "/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Aisha"))
                .andExpect(jsonPath("$.today").value("2026-11-13"))
                .andExpect(jsonPath("$.annualLeave.left").value(14))
                .andExpect(jsonPath("$.annualLeave.entitled").value(30))
                .andExpect(jsonPath("$.openTasks.count").value(7))
                .andExpect(jsonPath("$.openTasks.dueThisWeek").value(1))
                .andExpect(jsonPath("$.nextDue", hasSize(4)))
                .andExpect(jsonPath("$.nextDue[0].key").value("BAR-142"))
                .andExpect(jsonPath("$.nextDue[1].key").value("BAR-146"))
                .andExpect(jsonPath("$.pullRequestsAwaitingReview").value(3))
                .andExpect(jsonPath("$.outToday[*].name", containsInAnyOrder("Tom Becker", "Nadia Hussein", "Priya Nair")))
                .andExpect(jsonPath("$.outToday[?(@.name=='Tom Becker')].typeName").value("Sick"))
                .andExpect(jsonPath("$.outToday[?(@.name=='Tom Becker')].backOn").value("2026-11-17"))
                .andExpect(jsonPath("$.announcements", hasSize(2)))
                .andExpect(jsonPath("$.announcements[0].title").value("Year-end leave carry-over opens on 1 December"))
                .andExpect(jsonPath("$.announcements[0].postedBy").value("HR"));
    }

    @Test
    void dashboardForSomeoneWithoutGiteaOrTasks() throws Exception {
        mvc.perform(getAs("karim.aziz", "/api/dashboard"))
                .andExpect(jsonPath("$.annualLeave.left").value(9))
                .andExpect(jsonPath("$.openTasks.count").value(0))
                .andExpect(jsonPath("$.nextDue", hasSize(0)))
                .andExpect(jsonPath("$.pullRequestsAwaitingReview").value(0));
    }

    @Test
    void hrPostsAnnouncements() throws Exception {
        mvc.perform(postAs("aisha.rahman", "/api/announcements", "{\"title\":\"Hello\"}")).andExpect(status().isForbidden());
        mvc.perform(postAs("lena.fischer", "/api/announcements", "{\"title\":\"Office closed Friday\",\"body\":\"Public holiday\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.postedBy").value("HR"));
        mvc.perform(postAs("lena.fischer", "/api/announcements", "{\"body\":\"no title\"}")).andExpect(status().isBadRequest());
        mvc.perform(getAs("aisha.rahman", "/api/announcements")).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("Office closed Friday"));
    }
}

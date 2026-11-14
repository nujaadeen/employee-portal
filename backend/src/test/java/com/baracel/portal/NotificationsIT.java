package com.baracel.portal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.common.service.NotificationService;
import com.baracel.portal.support.IntegrationTest;
import com.baracel.portal.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationsIT extends IntegrationTest {
    @Autowired NotificationService notifications;

    private void seed(String user, int n) {
        for (int i = 1; i <= n; i++) notifications.notify(TestUsers.id(user), null, "TEST", "Note " + i, "body", "/");
    }

    @Test
    void listsWithTotalUnreadCountAndPaging() throws Exception {
        seed("mei.tan", 25);
        mvc.perform(getAs("mei.tan", "/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.unreadCount").value(25))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items", hasSize(20)));
        mvc.perform(getAs("mei.tan", "/api/notifications?page=1")).andExpect(jsonPath("$.items", hasSize(5)));
        mvc.perform(getAs("mei.tan", "/api/notifications?size=10&page=2")).andExpect(jsonPath("$.items", hasSize(5)));
        mvc.perform(getAs("mei.tan", "/api/notifications?size=500")).andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.items", hasSize(25)));
        mvc.perform(getAs("mei.tan", "/api/notifications?size=0")).andExpect(jsonPath("$.size").value(1));
        mvc.perform(getAs("mei.tan", "/api/notifications?page=-3")).andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void unreadFilterAndMarkingOneRead() throws Exception {
        seed("mei.tan", 3);
        Integer id = com.jayway.jsonpath.JsonPath.read(mvc.perform(getAs("mei.tan", "/api/notifications")).andReturn().getResponse().getContentAsString(), "$.items[0].id");
        mvc.perform(postAs("mei.tan", "/api/notifications/" + id + "/read", "{}")).andExpect(status().isOk());
        mvc.perform(getAs("mei.tan", "/api/notifications?unread=true"))
                .andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[*].read", everyItem(is(false))));
        mvc.perform(getAs("mei.tan", "/api/notifications")).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void readAllTouchesOnlyYourOwn() throws Exception {
        seed("mei.tan", 4);
        seed("sara.qureshi", 2);
        mvc.perform(postAs("mei.tan", "/api/notifications/read-all", "")).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(4));
        mvc.perform(getAs("mei.tan", "/api/notifications")).andExpect(jsonPath("$.unreadCount").value(0)).andExpect(jsonPath("$.total").value(4));
        mvc.perform(getAs("mei.tan", "/api/notifications?unread=true")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(getAs("sara.qureshi", "/api/notifications")).andExpect(jsonPath("$.unreadCount").value(2));
        mvc.perform(postAs("mei.tan", "/api/notifications/read-all", "")).andExpect(jsonPath("$.updated").value(0));
        mvc.perform(getAs("mei.tan", "/api/me")).andExpect(jsonPath("$.unreadNotifications").value(0));
    }

    @Test
    void notificationsArePrivateAndNeedASignIn() throws Exception {
        seed("mei.tan", 1);
        Integer id = com.jayway.jsonpath.JsonPath.read(mvc.perform(getAs("mei.tan", "/api/notifications")).andReturn().getResponse().getContentAsString(), "$.items[0].id");
        mvc.perform(postAs("sara.qureshi", "/api/notifications/" + id + "/read", "{}")).andExpect(status().isNotFound());
        mvc.perform(getAs("sara.qureshi", "/api/notifications")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
        String pending = custom("kc-n-1", "n@baracel.example", "N", "P");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/notifications").header("Authorization", pending))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.type", endsWith("/pending-approval")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/notifications/read-all").header("Authorization", pending))
                .andExpect(status().isForbidden());
    }
}

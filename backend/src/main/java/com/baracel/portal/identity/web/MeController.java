package com.baracel.portal.identity.web;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.access.service.AccessService;
import com.baracel.portal.access.service.AccessService.SignInMethodInfo;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.NotificationService;
import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Identity")
public class MeController {
    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    public record ManagerRef(UUID id, String name) {}
    public record MeResponse(UUID id, String code, String name, String email, @Nullable String jobTitle, @Nullable String department,
                             String status, List<String> roles, List<String> permissions, @Nullable ManagerRef manager,
                             List<SignInMethodInfo> signInMethods, long unreadNotifications) {}

    private final EmployeeService employees;
    private final AccessService access;
    private final NotificationService notifications;

    public MeController(EmployeeService employees, AccessService access, NotificationService notifications) {
        this.employees = employees;
        this.access = access;
        this.notifications = notifications;
    }

    @GetMapping
    public MeResponse me(CurrentUser me) {
        EmployeeView e = employees.require(me.employeeId());
        ManagerRef manager = employees.find(e.managerId()).map(m -> new ManagerRef(m.id(), m.fullName())).orElse(null);
        return new MeResponse(e.id(), e.code(), e.fullName(), e.email(), e.jobTitle(), e.department(), e.status().name(),
                me.roles().stream().filter(r -> !r.startsWith("default-roles") && !r.equals("offline_access") && !r.equals("uma_authorization")).sorted().toList(),
                me.permissions().stream().sorted().toList(), manager, signInMethods(me.keycloakId()),
                notifications.unreadCount(me.employeeId()));
    }

    /** Linked sign-in methods come from the Keycloak Admin API. If Keycloak cannot be reached the list is empty. */
    private List<SignInMethodInfo> signInMethods(String keycloakId) {
        try {
            return access.signInMethods(keycloakId);
        } catch (RuntimeException ex) {
            log.warn("Could not read sign-in methods from Keycloak: {}", ex.getMessage());
            return List.of();
        }
    }
}

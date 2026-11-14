package com.baracel.portal.employee.web;

import com.baracel.portal.access.service.KeycloakAdmin;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.employee.domain.EmployeeStatus;
import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeService.*;
import com.baracel.portal.employee.service.EmployeeView;
import com.baracel.portal.leave.service.LeaveService;
import com.baracel.portal.leave.service.LeaveService.LeaveRequestDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees")
public class EmployeeController {
    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    /** Directory scope fills only the first five fields. Team and records scopes add the rest. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmployeeItem(UUID id, String name, String jobTitle, String department, String email,
                               String code, String role, EmployeeStatus status, LocalDate joinedOn, UUID managerId,
                               String managerName, Integer annualLeaveLeft, String phone, String location, String giteaUsername) {}

    public record EmployeePage(List<EmployeeItem> items, int page, int size, long total, Map<String, Long> statusCounts) {}

    public record SignInMethodDto(String key, String label, boolean linked, Boolean mfa) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmployeeDetail(EmployeeItem employee, List<String> roles, List<SignInMethodDto> signInMethods,
                                 List<LeaveRequestDto> recentLeave) {}

    public record InviteRequest(@NotBlank @Email String email, @NotBlank String firstName, @NotBlank String lastName,
                                String jobTitle, String department, UUID managerId, String role) {}
    public record ApproveRequest(@NotBlank String role, UUID managerId, String jobTitle, String department) {}

    private final EmployeeService service;
    private final LeaveService leave;
    private final KeycloakAdmin keycloak;

    public EmployeeController(EmployeeService service, LeaveService leave, KeycloakAdmin keycloak) {
        this.service = service;
        this.leave = leave;
        this.keycloak = keycloak;
    }

    @GetMapping
    public EmployeePage list(CurrentUser me, @RequestParam(defaultValue = "directory") String scope,
                             @RequestParam(required = false) String q, @RequestParam(required = false) EmployeeStatus status,
                             @RequestParam(required = false) String department, @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "25") int size) {
        Scope s;
        try { s = Scope.valueOf(scope.toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("invalid-scope", "scope must be directory, team or all"); }
        Page<EmployeeView> result = service.search(me, s, q, status, department, page, size);
        List<EmployeeView> rows = result.getContent();
        Map<UUID, EmployeeView> managers = service.views(rows.stream().map(EmployeeView::managerId).toList());
        Map<UUID, Integer> left = s == Scope.DIRECTORY ? Map.of() : leave.annualLeft(rows.stream().map(EmployeeView::id).toList());
        List<EmployeeItem> items = rows.stream().map(v -> item(v, s, managers, left)).toList();
        Map<String, Long> counts = null;
        if (s == Scope.ALL) {
            counts = new LinkedHashMap<>();
            Map<EmployeeStatus, Long> raw = service.statusCounts();
            counts.put("ALL", raw.values().stream().mapToLong(Long::longValue).sum());
            for (EmployeeStatus st : EmployeeStatus.values()) counts.put(st.name(), raw.getOrDefault(st, 0L));
        }
        return new EmployeePage(items, result.getNumber(), result.getSize(), result.getTotalElements(), counts);
    }

    @GetMapping("/{id}")
    public EmployeeDetail get(CurrentUser me, @PathVariable UUID id) {
        EmployeeView v = service.getVisible(me, id);
        Map<UUID, EmployeeView> managers = service.views(List.of(v.managerId() == null ? v.id() : v.managerId()));
        EmployeeItem item = item(v, Scope.ALL, managers, leave.annualLeft(List.of(v.id())));
        boolean hr = me.has("employee:records");
        List<String> roles = null;
        List<SignInMethodDto> methods = null;
        if (hr) {
            try {
                roles = keycloak.userRoles(v.keycloakId()).stream().sorted().toList();
                Set<String> linked = keycloak.linkedProviders(v.keycloakId());
                KeycloakAdmin.CredentialInfo c = keycloak.credentials(v.keycloakId());
                methods = List.of(new SignInMethodDto("google", "Google", linked.contains("google"), null),
                        new SignInMethodDto("github", "GitHub", linked.contains("github"), null),
                        new SignInMethodDto("facebook", "Facebook", linked.contains("facebook"), null),
                        new SignInMethodDto("password", "Email and password", c.password(), c.otp()));
            } catch (RuntimeException e) {
                log.warn("Keycloak details unavailable for {}: {}", v.code(), e.getMessage());
            }
        }
        return new EmployeeDetail(item, roles, methods, leave.recentFor(v.id()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('employee:manage')")
    public EmployeeItem update(CurrentUser me, @PathVariable UUID id, @RequestBody UpdateEmployee body) {
        EmployeeView v = service.update(me, id, body);
        return item(v, Scope.ALL, service.views(List.of(v.managerId() == null ? v.id() : v.managerId())), Map.of());
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('employee:manage')")
    public ResponseEntity<EmployeeItem> invite(CurrentUser me, @Valid @RequestBody InviteRequest body) {
        EmployeeView v = service.invite(me, new InviteEmployee(body.email(), body.firstName(), body.lastName(), body.jobTitle(),
                body.department(), body.managerId(), body.role()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(item(v, Scope.ALL, service.views(List.of(v.managerId() == null ? v.id() : v.managerId())), Map.of()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('employee:manage')")
    public EmployeeItem approve(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody ApproveRequest body) {
        EmployeeView v = service.approve(me, id, new ApproveEmployee(body.role(), body.managerId(), body.jobTitle(), body.department()));
        return item(v, Scope.ALL, service.views(List.of(v.managerId() == null ? v.id() : v.managerId())), Map.of());
    }

    private static EmployeeItem item(EmployeeView v, Scope scope, Map<UUID, EmployeeView> managers, Map<UUID, Integer> left) {
        if (scope == Scope.DIRECTORY) return new EmployeeItem(v.id(), v.fullName(), v.jobTitle(), v.department(), v.email(),
                null, null, null, null, null, null, null, null, null, null);
        EmployeeView m = v.managerId() == null ? null : managers.get(v.managerId());
        boolean all = scope == Scope.ALL;
        return new EmployeeItem(v.id(), v.fullName(), v.jobTitle(), v.department(), v.email(), v.code(), v.primaryRole(),
                v.status(), v.joinedOn(), v.managerId(), m == null ? null : m.fullName(), left.get(v.id()),
                all ? v.phone() : null, all ? v.location() : null, all ? v.giteaUsername() : null);
    }
}

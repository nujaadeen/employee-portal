package com.baracel.portal.employee.service;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.access.service.AccessService;
import com.baracel.portal.access.service.KeycloakAdmin;
import com.baracel.portal.access.service.PortalRoles;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.AuditService;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.employee.domain.Employee;
import com.baracel.portal.employee.domain.EmployeeStatus;
import com.baracel.portal.employee.repo.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
    public enum Scope { DIRECTORY, TEAM, ALL }

    /** Roles HR may hand out. HR and ADMIN need the access:manage permission. */
    private static final Set<String> HR_ASSIGNABLE = Set.of(PortalRoles.EMPLOYEE, PortalRoles.SALES, PortalRoles.ENGINEER, PortalRoles.MANAGER);

    public record UpdateEmployee(@Nullable String jobTitle, @Nullable String department, @Nullable UUID managerId, @Nullable String phone, @Nullable String location,
                                 @Nullable String giteaUsername, @Nullable EmployeeStatus status, @Nullable LocalDate joinedOn) {}
    public record InviteEmployee(String email, String firstName, String lastName, String jobTitle, String department,
                                 UUID managerId, String role) {}
    public record ApproveEmployee(String role, UUID managerId, String jobTitle, String department) {}

    private final EmployeeRepository repo;
    private final KeycloakAdmin keycloak;
    private final AuditService audit;
    private final Clock clock;
    private final AccessService access;

    public EmployeeService(EmployeeRepository repo, KeycloakAdmin keycloak, AuditService audit, Clock clock, AccessService access) {
        this.access = access;
        this.repo = repo;
        this.keycloak = keycloak;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- identity support ----

    /** Just-in-time provisioning: first authenticated call of a JWT sub creates a PENDING_APPROVAL employee. */
    @Transactional
    public EmployeeView provisionIfAbsent(String sub, String email, String first, String last) {
        return repo.findByKeycloakId(sub).map(EmployeeView::of).orElseGet(() -> {
            Employee e = new Employee();
            e.id = UUID.randomUUID();
            e.keycloakId = sub;
            e.code = String.format("EMP-%04d", repo.nextCodeNumber());
            String safeEmail = email == null || email.isBlank() ? sub + "@unknown.invalid" : email;
            e.email = safeEmail;
            e.firstName = first == null || first.isBlank() ? safeEmail.split("@")[0] : first;
            e.lastName = last == null ? "" : last;
            e.status = EmployeeStatus.PENDING_APPROVAL;
            e.primaryRole = PortalRoles.EMPLOYEE;
            repo.saveAndFlush(e);
            return EmployeeView.of(e);
        });
    }

    @Transactional(readOnly = true)
    public EmployeeView requireByKeycloakId(String sub) {
        return repo.findByKeycloakId(sub).map(EmployeeView::of).orElseThrow(() -> ApiException.notFound("Employee not found"));
    }

    @Transactional(readOnly = true)
    public EmployeeView require(UUID id) {
        return repo.findById(id).map(EmployeeView::of).orElseThrow(() -> ApiException.notFound("Employee not found"));
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeView> find(UUID id) { return id == null ? Optional.empty() : repo.findById(id).map(EmployeeView::of); }

    @Transactional(readOnly = true)
    public Map<UUID, EmployeeView> views(Collection<UUID> ids) {
        Set<UUID> clean = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (clean.isEmpty()) return Map.of();
        return repo.findAllById(clean).stream().map(EmployeeView::of).collect(Collectors.toMap(EmployeeView::id, v -> v));
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> directReports(UUID managerId) {
        return repo.findByManagerIdOrderByFirstNameAsc(managerId).stream().map(EmployeeView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> inDepartment(String department) {
        if (department == null) return List.of();
        return repo.findByDepartment(department).stream().map(EmployeeView::of).toList();
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeView> findByGiteaUsername(String username) {
        return username == null ? Optional.empty() : repo.findFirstByGiteaUsernameIgnoreCase(username).map(EmployeeView::of);
    }

    // ---- directory, team and records ----

    @Transactional(readOnly = true)
    public Page<EmployeeView> search(CurrentUser me, Scope scope, String q, EmployeeStatus status, String department, int page, int size) {
        switch (scope) {
            case DIRECTORY -> require(me, "employee:directory");
            case TEAM -> require(me, "employee:team");
            case ALL -> require(me, "employee:records");
        }
        List<Specification<Employee>> parts = new ArrayList<>();
        if (scope == Scope.TEAM) {
            // The manager id always comes from the token, never from the request.
            UUID mine = me.employeeId();
            parts.add((r, cq, cb) -> cb.equal(r.get("managerId"), mine));
        }
        if (scope == Scope.DIRECTORY)
            parts.add((r, cq, cb) -> cb.not(r.get("status").in(EmployeeStatus.PENDING_APPROVAL, EmployeeStatus.DEACTIVATED)));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            parts.add((r, cq, cb) -> {
                Predicate name = cb.like(cb.lower(cb.concat(cb.concat(r.<String>get("firstName"), " "), r.<String>get("lastName"))), like);
                Predicate email = cb.like(cb.lower(r.<String>get("email")), like);
                Predicate dept = cb.like(cb.lower(cb.coalesce(r.<String>get("department"), "")), like);
                return cb.or(name, email, dept);
            });
        }
        if (status != null) parts.add((r, cq, cb) -> cb.equal(r.get("status"), status));
        if (department != null && !department.isBlank()) parts.add((r, cq, cb) -> cb.equal(r.get("department"), department));
        int safeSize = Math.min(Math.max(size, 1), 100);
        return repo.findAll(Specification.allOf(parts),
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("firstName").ascending().and(Sort.by("lastName"))))
                .map(EmployeeView::of);
    }

    @Transactional(readOnly = true)
    public Map<EmployeeStatus, Long> statusCounts() {
        Map<EmployeeStatus, Long> out = new EnumMap<>(EmployeeStatus.class);
        for (Object[] row : repo.countByStatus()) out.put((EmployeeStatus) row[0], (Long) row[1]);
        return out;
    }

    /** The person themselves, a direct report of the caller, or someone with HR records access. */
    @Transactional(readOnly = true)
    public EmployeeView getVisible(CurrentUser me, UUID id) {
        EmployeeView target = require(id);
        boolean self = target.id().equals(me.employeeId());
        boolean report = me.employeeId().equals(target.managerId());
        if (!self && !report && !me.has("employee:records")) throw ApiException.forbidden("You cannot see this record.");
        return target;
    }

    // ---- HR changes ----

    @Transactional
    public EmployeeView update(CurrentUser me, UUID id, UpdateEmployee u) {
        Employee e = repo.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
        if (u.managerId() != null) {
            if (u.managerId().equals(id)) throw ApiException.badRequest("invalid-manager", "A person cannot manage themselves.");
            Employee m = repo.findById(u.managerId()).orElseThrow(() -> ApiException.badRequest("invalid-manager", "Manager not found."));
            for (Employee up = m; up != null; up = up.managerId == null ? null : repo.findById(up.managerId).orElse(null))
                if (up.id.equals(id)) throw ApiException.badRequest("invalid-manager", "That would create a reporting loop.");
            e.managerId = u.managerId();
        }
        if (u.jobTitle() != null) e.jobTitle = u.jobTitle();
        if (u.department() != null) e.department = u.department();
        if (u.phone() != null) e.phone = u.phone();
        if (u.location() != null) e.location = u.location();
        if (u.giteaUsername() != null) e.giteaUsername = u.giteaUsername().isBlank() ? null : u.giteaUsername();
        if (u.joinedOn() != null) e.joinedOn = u.joinedOn();
        if (u.status() != null) {
            if (u.status() == EmployeeStatus.PENDING_APPROVAL)
                throw ApiException.badRequest("invalid-status", "PENDING_APPROVAL is set only by sign-up.");
            e.status = u.status();
        }
        audit.log(me, "EMPLOYEE_UPDATED", "employee", e.code, "fields changed by HR");
        return EmployeeView.of(e);
    }

    @Transactional
    public EmployeeView invite(CurrentUser me, InviteEmployee in) {
        String role = in.role() == null ? PortalRoles.EMPLOYEE : in.role();
        checkAssignable(me, role);
        if (repo.findByEmailIgnoreCase(in.email()).isPresent())
            throw ApiException.conflict("employee-exists", "An employee with this email already exists.");
        if (in.managerId() != null) require(in.managerId());
        String kcId = keycloak.createUser(in.email(), in.firstName(), in.lastName());
        keycloak.setUserRoles(kcId, rolesFor(role));
        Employee e = new Employee();
        e.id = UUID.randomUUID();
        e.keycloakId = kcId;
        e.code = String.format("EMP-%04d", repo.nextCodeNumber());
        e.firstName = in.firstName();
        e.lastName = in.lastName();
        e.email = in.email();
        e.jobTitle = in.jobTitle();
        e.department = in.department();
        e.managerId = in.managerId();
        e.status = EmployeeStatus.ONBOARDING;
        e.primaryRole = role;
        repo.saveAndFlush(e);
        audit.log(me, "EMPLOYEE_INVITED", "employee", e.code, "role=" + role);
        return EmployeeView.of(e);
    }

    @Transactional
    public EmployeeView approve(CurrentUser me, UUID id, ApproveEmployee a) {
        checkAssignable(me, a.role());
        Employee e = repo.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
        if (e.status != EmployeeStatus.PENDING_APPROVAL && e.status != EmployeeStatus.ONBOARDING)
            throw ApiException.conflict("not-pending", "This person is not waiting for approval.");
        if (a.managerId() != null) {
            if (a.managerId().equals(id)) throw ApiException.badRequest("invalid-manager", "A person cannot manage themselves.");
            require(a.managerId());
            e.managerId = a.managerId();
        }
        if (a.jobTitle() != null) e.jobTitle = a.jobTitle();
        if (a.department() != null) e.department = a.department();
        keycloak.setUserRoles(e.keycloakId, rolesFor(a.role()));
        e.primaryRole = a.role();
        e.status = EmployeeStatus.ACTIVE;
        if (e.joinedOn == null) e.joinedOn = LocalDate.now(clock);
        audit.log(me, "EMPLOYEE_APPROVED", "employee", e.code, "role=" + a.role() + " manager=" + a.managerId());
        return EmployeeView.of(e);
    }

    /**
     * Sends Keycloak's "update your password and verify your email" mail. No password is created, returned or logged.
     * Only someone who can manage access may do this for a person whose roles include access:manage (an administrator).
     */
    @Transactional
    public void resetAccess(CurrentUser me, UUID id) {
        Employee e = repo.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
        boolean targetIsAdmin = access.permissionsForRoles(keycloak.userRoles(e.keycloakId)).contains("access:manage");
        if (targetIsAdmin && !me.has("access:manage")) throw ApiException.forbidden("Only an administrator can reset access for an administrator.");
        try {
            keycloak.sendActionsEmail(e.keycloakId, List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "keycloak-unavailable", "Keycloak could not send the email.");
        }
        audit.log(me, "EMPLOYEE_ACCESS_RESET", "employee", e.code, "update-password and verify-email mail requested");
    }

    private void checkAssignable(CurrentUser me, String role) {
        if (role == null || !PortalRoles.ALL.contains(role)) throw ApiException.badRequest("unknown-role", "Unknown role: " + role);
        if (!HR_ASSIGNABLE.contains(role) && !me.has("access:manage"))
            throw ApiException.forbidden("Only an administrator can assign the " + role + " role.");
    }

    private Set<String> rolesFor(String role) {
        Set<String> s = new TreeSet<>(Set.of(PortalRoles.EMPLOYEE));
        s.add(role);
        return s;
    }

    /** Used by the daily leave job: ACTIVE -> ON_LEAVE for people on approved leave, ON_LEAVE -> ACTIVE for the rest. */
    @Transactional
    public int syncLeaveStatus(Set<UUID> onLeaveToday) {
        int changed = 0;
        for (Employee e : repo.findAll()) {
            if (e.status == EmployeeStatus.ACTIVE && onLeaveToday.contains(e.id)) { e.status = EmployeeStatus.ON_LEAVE; changed++; }
            else if (e.status == EmployeeStatus.ON_LEAVE && !onLeaveToday.contains(e.id)) { e.status = EmployeeStatus.ACTIVE; changed++; }
        }
        return changed;
    }

    private static void require(CurrentUser me, String permission) {
        if (!me.has(permission)) throw ApiException.forbidden("You do not have the " + permission + " permission.");
    }
}

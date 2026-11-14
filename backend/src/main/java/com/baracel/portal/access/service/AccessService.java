package com.baracel.portal.access.service;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.access.domain.CustomRole;
import com.baracel.portal.access.domain.Permission;
import com.baracel.portal.access.domain.RolePermission;
import com.baracel.portal.access.repo.CustomRoleRepository;
import com.baracel.portal.access.repo.PermissionRepository;
import com.baracel.portal.access.repo.RolePermissionRepository;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.AuditService;
import com.baracel.portal.common.web.ApiException;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessService {
    private static final Logger log = LoggerFactory.getLogger(AccessService.class);

    public record RoleSummary(String name, @Nullable String description, boolean builtIn, @Nullable Integer people) {}
    public record MatrixPermission(String key, String module, String action, String label, @Nullable String hint, String group,
                                   String row, String column, boolean granted) {}
    public record RoleMatrix(String role, @Nullable Integer people, List<MatrixPermission> permissions) {}
    public record IdentityProviderInfo(String alias, String displayName, boolean enabled, boolean editable) {}
    public record SignInMethodInfo(String key, String label, boolean linked, MfaState mfa) {}
    public record AccessSettings(String defaultRoleForNewSignUps, String approver, int sessionLengthHours) {}

    private static final java.util.regex.Pattern ROLE_NAME = java.util.regex.Pattern.compile("^[A-Z_]{3,30}$");

    private final CustomRoleRepository customRoles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final KeycloakAdmin keycloak;
    private final AuditService audit;
    private final String defaultRole;
    private final String approver;
    private final int sessionHours;

    private volatile Map<String, Set<String>> cache;

    public AccessService(CustomRoleRepository customRoles, PermissionRepository permissions, RolePermissionRepository rolePermissions, KeycloakAdmin keycloak,
                         AuditService audit, @Value("${portal.access.default-role:EMPLOYEE}") String defaultRole,
                         @Value("${portal.access.approver:HR}") String approver,
                         @Value("${portal.access.session-hours:8}") int sessionHours) {
        this.customRoles = customRoles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.keycloak = keycloak;
        this.audit = audit;
        this.defaultRole = defaultRole;
        this.approver = approver;
        this.sessionHours = sessionHours;
    }

    // ---- role -> permission lookup, used by the JWT converter (cached) ----

    /** A user's permissions are the union of the permissions of all their roles. */
    public Set<String> permissionsForRoles(Collection<String> roles) {
        Map<String, Set<String>> c = load();
        Set<String> out = new TreeSet<>();
        for (String r : roles) out.addAll(c.getOrDefault(r, Set.of()));
        return out;
    }

    private Map<String, Set<String>> load() {
        Map<String, Set<String>> c = cache;
        if (c != null) return c;
        Map<Long, String> keys = permissions.findAll().stream().collect(Collectors.toMap(p -> p.id, Permission::key));
        Map<String, Set<String>> m = new HashMap<>();
        for (RolePermission rp : rolePermissions.findAll())
            m.computeIfAbsent(rp.roleName, k -> new HashSet<>()).add(keys.get(rp.permissionId));
        cache = m;
        return m;
    }

    public void clearCache() { cache = null; }

    /** Linked sign-in methods of a person, read from Keycloak. Social providers are PROVIDER_MANAGED, the password depends on OTP or WebAuthn. */
    public List<SignInMethodInfo> signInMethods(String keycloakUserId) {
        Set<String> linked = keycloak.linkedProviders(keycloakUserId);
        KeycloakAdmin.CredentialInfo cred = keycloak.credentials(keycloakUserId);
        List<SignInMethodInfo> out = new ArrayList<>();
        out.add(new SignInMethodInfo("google", "Google", linked.contains("google"), MfaState.PROVIDER_MANAGED));
        out.add(new SignInMethodInfo("github", "GitHub", linked.contains("github"), MfaState.PROVIDER_MANAGED));
        out.add(new SignInMethodInfo("facebook", "Facebook", linked.contains("facebook"), MfaState.PROVIDER_MANAGED));
        out.add(new SignInMethodInfo("password", "Email and password", cred.password(), cred.otp() ? MfaState.ENABLED : MfaState.DISABLED));
        return out;
    }

    // ---- admin screens ----

    @Transactional(readOnly = true)
    public List<RoleSummary> roles() {
        List<RoleSummary> out = new ArrayList<>();
        PortalRoles.ALL.forEach(r -> out.add(new RoleSummary(r, null, true, count(r))));
        customRoles.findAllByOrderByNameAsc().forEach(c -> out.add(new RoleSummary(c.name, c.description, false, count(c.name))));
        return out;
    }

    /** Creates a Keycloak realm role and an empty permission set. */
    @Transactional
    public RoleSummary createRole(CurrentUser actor, String name, String description) {
        if (name == null || !ROLE_NAME.matcher(name).matches())
            throw ApiException.badRequest("invalid-role-name", "A role name has 3 to 30 characters: capital letters and underscores only.");
        if (PortalRoles.ALL.contains(name) || customRoles.existsById(name) || keycloak.realmRoleNames().contains(name))
            throw ApiException.conflict("role-exists", "A role named " + name + " already exists.");
        keycloak.createRole(name, description);
        CustomRole c = new CustomRole();
        c.name = name;
        c.description = description == null || description.isBlank() ? null : description.trim();
        customRoles.save(c);
        audit.log(actor, "ROLE_CREATED", "role", name, c.description);
        return new RoleSummary(name, c.description, false, 0);
    }

    /** Only custom roles, and only when nobody holds the role. */
    @Transactional
    public void deleteRole(CurrentUser actor, String name) {
        if (PortalRoles.ALL.contains(name)) throw ApiException.badRequest("built-in-role", name + " is a built-in role and cannot be deleted.");
        CustomRole c = customRoles.findById(name).orElseThrow(() -> ApiException.notFound("Unknown role: " + name));
        if (keycloak.countUsersWithRole(name) > 0) throw ApiException.conflict("role-in-use", "Some people still have the " + name + " role. Remove it from them first.");
        keycloak.deleteRole(name);
        rolePermissions.deleteAllByRole(name);
        customRoles.delete(c);
        clearCache();
        audit.log(actor, "ROLE_DELETED", "role", name, null);
    }

    private Integer count(String role) {
        try {
            return PortalRoles.EMPLOYEE.equals(role) ? keycloak.countUsers() : keycloak.countUsersWithRole(role);
        } catch (RuntimeException e) {
            log.warn("Could not read user count for {} from Keycloak: {}", role, e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public RoleMatrix matrix(String role) {
        requireRole(role);
        Set<Long> granted = rolePermissions.findByRoleName(role).stream().map(rp -> rp.permissionId).collect(Collectors.toSet());
        List<MatrixPermission> rows = permissions.findAllByOrderBySortOrderAsc().stream()
                .map(p -> new MatrixPermission(p.key(), p.module, p.action, p.label, p.hint, p.uiGroup, p.uiRow, p.uiColumn,
                        granted.contains(p.id))).toList();
        return new RoleMatrix(role, count(role), rows);
    }

    @Transactional
    public RoleMatrix updateMatrix(CurrentUser actor, String role, Set<String> wanted) {
        requireRole(role);
        Map<String, Permission> byKey = permissions.findAll().stream().collect(Collectors.toMap(Permission::key, p -> p));
        for (String k : wanted)
            if (!byKey.containsKey(k)) throw ApiException.badRequest("unknown-permission", "Unknown permission: " + k);
        if (PortalRoles.ADMIN.equals(role) && !wanted.contains("access:manage"))
            throw ApiException.badRequest("lockout", "ADMIN must keep access:manage, otherwise nobody could edit access.");

        Set<Long> current = rolePermissions.findByRoleName(role).stream().map(rp -> rp.permissionId).collect(Collectors.toSet());
        Set<Long> target = wanted.stream().map(k -> byKey.get(k).id).collect(Collectors.toSet());
        Set<Long> remove = new HashSet<>(current); remove.removeAll(target);
        Set<Long> add = new HashSet<>(target); add.removeAll(current);
        if (!remove.isEmpty()) rolePermissions.deleteByRoleAndPermissionIds(role, remove);
        for (Long id : add) rolePermissions.save(new RolePermission(role, id));
        rolePermissions.flush();
        clearCache();
        Map<Long, String> keyById = byKey.values().stream().collect(Collectors.toMap(p -> p.id, Permission::key));
        audit.log(actor, "ACCESS_MATRIX_UPDATED", "role", role,
                "added=" + add.stream().map(keyById::get).sorted().toList() + " removed=" + remove.stream().map(keyById::get).sorted().toList());
        return matrix(role);
    }

    private void requireRole(String role) {
        if (!PortalRoles.ALL.contains(role) && !customRoles.existsById(role)) throw ApiException.notFound("Unknown role: " + role);
    }

    public List<IdentityProviderInfo> identityProviders() {
        List<IdentityProviderInfo> out = new ArrayList<>();
        for (KeycloakAdmin.IdpInfo i : keycloak.identityProviders())
            out.add(new IdentityProviderInfo(i.alias(), i.displayName(), i.enabled(), true));
        out.add(new IdentityProviderInfo("password", "Email and password", true, false));
        return out;
    }

    public List<IdentityProviderInfo> updateIdentityProviders(CurrentUser actor, Map<String, Boolean> changes) {
        Set<String> known = new HashSet<>();
        for (KeycloakAdmin.IdpInfo i : keycloak.identityProviders()) known.add(i.alias());
        for (String alias : changes.keySet())
            if (!known.contains(alias)) throw ApiException.badRequest("unknown-provider", "Provider cannot be changed here: " + alias);
        changes.forEach((alias, enabled) -> {
            keycloak.setIdentityProviderEnabled(alias, enabled);
            audit.log(actor, "IDENTITY_PROVIDER_" + (enabled ? "ENABLED" : "DISABLED"), "identity-provider", alias, null);
        });
        return identityProviders();
    }

    public AccessSettings settings() {
        int hours = sessionHours;
        try { hours = keycloak.realm().ssoSessionMaxLifespanSeconds() / 3600; } catch (RuntimeException e) {
            log.warn("Could not read the realm session length: {}", e.getMessage());
        }
        return new AccessSettings(defaultRole + " (pending)", approver, hours);
    }
}

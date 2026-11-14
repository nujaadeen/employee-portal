package com.baracel.portal.support;

import com.baracel.portal.access.service.KeycloakAdmin;
import java.util.*;

public class FakeKeycloakAdmin implements KeycloakAdmin {
    public final Map<String, Boolean> idpEnabled = new LinkedHashMap<>(Map.of("google", true, "github", true, "facebook", true));
    public final Map<String, Set<String>> rolesSet = new HashMap<>();
    public final List<String> createdEmails = new ArrayList<>();
    public final List<String> actionEmails = new ArrayList<>();
    public final Set<String> roles = new TreeSet<>(List.of("EMPLOYEE", "MANAGER", "HR", "SALES", "ENGINEER", "ADMIN", "offline_access"));
    public final Map<String, Integer> roleUsers = new HashMap<>();

    @Override public Set<String> linkedProviders(String userId) { return Set.of("google"); }
    public boolean otp = false;
    @Override public CredentialInfo credentials(String userId) { return new CredentialInfo(true, otp); }
    @Override public List<IdpInfo> identityProviders() {
        return idpEnabled.entrySet().stream().map(e -> new IdpInfo(e.getKey(), e.getKey(), e.getValue())).toList();
    }
    @Override public void setIdentityProviderEnabled(String alias, boolean enabled) { idpEnabled.put(alias, enabled); }
    @Override public int countUsersWithRole(String role) { return roleUsers.getOrDefault(role, List.of("EMPLOYEE", "MANAGER", "HR", "SALES", "ENGINEER", "ADMIN").contains(role) ? 3 : 0); }
    @Override public int countUsers() { return 13; }
    @Override public Set<String> userRoles(String userId) { return rolesSet.getOrDefault(userId, Set.of("EMPLOYEE")); }
    @Override public void setUserRoles(String userId, Set<String> roles) { rolesSet.put(userId, new TreeSet<>(roles)); }
    @Override public String createUser(String email, String firstName, String lastName) {
        createdEmails.add(email);
        return UUID.randomUUID().toString();
    }
    @Override public void sendActionsEmail(String userId, List<String> actions) { actionEmails.add(userId + ":" + String.join(",", actions)); }
    @Override public Set<String> realmRoleNames() { return roles; }
    @Override public void createRole(String name, String description) { roles.add(name); }
    @Override public void deleteRole(String name) { roles.remove(name); }
    @Override public RealmInfo realm() { return new RealmInfo(28800); }

    public void reset() {
        idpEnabled.replaceAll((k, v) -> true);
        rolesSet.clear();
        createdEmails.clear();
        actionEmails.clear();
        otp = false;
        roleUsers.clear();
        roles.removeIf(r -> !List.of("EMPLOYEE", "MANAGER", "HR", "SALES", "ENGINEER", "ADMIN", "offline_access").contains(r));
    }
}

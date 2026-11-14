package com.baracel.portal.support;

import com.baracel.portal.access.service.KeycloakAdmin;
import java.util.*;

public class FakeKeycloakAdmin implements KeycloakAdmin {
    public final Map<String, Boolean> idpEnabled = new LinkedHashMap<>(Map.of("google", true, "github", true, "facebook", true));
    public final Map<String, Set<String>> rolesSet = new HashMap<>();
    public final List<String> createdEmails = new ArrayList<>();

    @Override public Set<String> linkedProviders(String userId) { return Set.of("google"); }
    @Override public CredentialInfo credentials(String userId) { return new CredentialInfo(true, false); }
    @Override public List<IdpInfo> identityProviders() {
        return idpEnabled.entrySet().stream().map(e -> new IdpInfo(e.getKey(), e.getKey(), e.getValue())).toList();
    }
    @Override public void setIdentityProviderEnabled(String alias, boolean enabled) { idpEnabled.put(alias, enabled); }
    @Override public int countUsersWithRole(String role) { return 3; }
    @Override public int countUsers() { return 13; }
    @Override public Set<String> userRoles(String userId) { return rolesSet.getOrDefault(userId, Set.of("EMPLOYEE")); }
    @Override public void setUserRoles(String userId, Set<String> roles) { rolesSet.put(userId, new TreeSet<>(roles)); }
    @Override public String createUser(String email, String firstName, String lastName) {
        createdEmails.add(email);
        return UUID.randomUUID().toString();
    }
    @Override public RealmInfo realm() { return new RealmInfo(28800); }

    public void reset() {
        idpEnabled.replaceAll((k, v) -> true);
        rolesSet.clear();
        createdEmails.clear();
    }
}

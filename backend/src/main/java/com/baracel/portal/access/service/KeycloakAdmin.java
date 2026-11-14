package com.baracel.portal.access.service;

import java.util.List;
import java.util.Set;

/**
 * Port to the Keycloak Admin API (called with the portal-service client). People are addressed only by their
 * Keycloak user id, never by Keycloak's database, so LDAP federation can be added later.
 */
public interface KeycloakAdmin {
    record IdpInfo(String alias, String displayName, boolean enabled) {}
    record CredentialInfo(boolean password, boolean otp) {}
    record RealmInfo(int ssoSessionMaxLifespanSeconds) {}

    Set<String> linkedProviders(String userId);
    CredentialInfo credentials(String userId);
    List<IdpInfo> identityProviders();
    void setIdentityProviderEnabled(String alias, boolean enabled);
    int countUsersWithRole(String role);
    int countUsers();
    Set<String> userRoles(String userId);
    /** Replaces the user's portal roles (EMPLOYEE, MANAGER, ...) with the given set. Other roles are left alone. */
    void setUserRoles(String userId, Set<String> roles);
    /** Creates the user with required actions and sends the action email. Returns the new Keycloak user id. */
    String createUser(String email, String firstName, String lastName);
    RealmInfo realm();
    /** Sends Keycloak's execute-actions email (for example UPDATE_PASSWORD, VERIFY_EMAIL). No password is ever handled here. */
    void sendActionsEmail(String userId, List<String> actions);
    Set<String> realmRoleNames();
    void createRole(String name, String description);
    void deleteRole(String name);
}

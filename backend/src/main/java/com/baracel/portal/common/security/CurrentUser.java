package com.baracel.portal.common.security;

import java.util.Set;
import java.util.UUID;

/** The signed-in person for the current request: JWT identity plus the employee row. */
public record CurrentUser(UUID employeeId, String keycloakId, String name, String email, String status,
                          Set<String> roles, Set<String> permissions) {
    public boolean has(String permission) { return permissions.contains(permission); }
}

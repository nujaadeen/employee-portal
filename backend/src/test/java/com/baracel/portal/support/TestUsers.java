package com.baracel.portal.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The seeded people. Ids equal the Keycloak user ids of the realm export. */
public final class TestUsers {
    public record U(String username, UUID id, List<String> roles) {}

    public static final Map<String, U> ALL = Map.ofEntries(
            u("aisha.rahman", 1, "SALES"), u("omar.haddad", 2, "MANAGER"), u("ruth.mensah", 3, "MANAGER"),
            u("lena.fischer", 4, "HR"), u("daniel.okoye", 5, "ADMIN", "ENGINEER", "MANAGER"), u("priya.nair", 6, "ENGINEER"),
            u("tom.becker", 7, "ENGINEER"), u("sara.qureshi", 8), u("mei.tan", 9), u("karim.aziz", 10, "SALES"),
            u("nadia.hussein", 11, "SALES"), u("joel.mathews", 12, "SALES"), u("hana.saleh", 13, "SALES"));

    private static Map.Entry<String, U> u(String name, int n, String... roles) {
        List<String> all = new java.util.ArrayList<>(List.of("EMPLOYEE"));
        all.addAll(List.of(roles));
        return Map.entry(name, new U(name, UUID.fromString(String.format("b0000000-0000-4000-8000-%012d", n)), all));
    }

    public static UUID id(String username) { return ALL.get(username).id(); }

    private TestUsers() {}
}

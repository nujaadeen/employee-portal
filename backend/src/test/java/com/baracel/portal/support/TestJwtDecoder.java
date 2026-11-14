package com.baracel.portal.support;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test tokens (no signature). "user~aisha.rahman" is a seeded person.
 * "custom~" + base64url("SUB:EMAIL:FIRST:LAST:ROLE1,ROLE2") is a brand new sign-in that has no employee row yet.
 * The real JwtAuthenticationConverter, the provisioning filter and the method security still run.
 */
public class TestJwtDecoder implements JwtDecoder {
    @Override
    public Jwt decode(String token) {
        String[] p = token.startsWith("custom~")
                ? ("custom:" + new String(java.util.Base64.getUrlDecoder().decode(token.substring(7)), java.nio.charset.StandardCharsets.UTF_8)).split(":")
                : token.replace("user~", "user:").split(":");
        Map<String, Object> claims;
        if (p[0].equals("user") && TestUsers.ALL.containsKey(p[1])) {
            TestUsers.U u = TestUsers.ALL.get(p[1]);
            String[] n = p[1].split("\\.");
            claims = Map.of("sub", u.id().toString(), "email", p[1] + "@baracel.example", "given_name", cap(n[0]), "family_name", cap(n[1]),
                    "realm_access", Map.of("roles", u.roles()));
        } else if (p[0].equals("custom") && p.length >= 5) {
            List<String> roles = p.length > 5 ? Arrays.asList(p[5].split(",")) : List.of("EMPLOYEE");
            claims = Map.of("sub", p[1], "email", p[2], "given_name", p[3], "family_name", p[4], "realm_access", Map.of("roles", roles));
        } else {
            throw new BadJwtException("Unknown test token");
        }
        return new Jwt(token, Instant.now(), Instant.now().plusSeconds(600), Map.of("alg", "none"), claims);
    }

    private static String cap(String s) { return s.substring(0, 1).toUpperCase() + s.substring(1); }
}

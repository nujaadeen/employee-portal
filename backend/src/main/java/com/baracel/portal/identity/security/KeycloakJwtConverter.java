package com.baracel.portal.identity.security;

import com.baracel.portal.access.service.AccessService;
import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authorities = permission strings of the user's realm roles (union, loaded from the access module and cached),
 * plus ROLE_&lt;name&gt; for each realm role.
 */
@Component
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AccessService access;

    public KeycloakJwtConverter(AccessService access) { this.access = access; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = realmRoles(jwt);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        access.permissionsForRoles(roles).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    static Set<String> realmRoles(Jwt jwt) {
        Object ra = jwt.getClaims().get("realm_access");
        if (ra instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> c) {
            Set<String> out = new TreeSet<>();
            c.forEach(o -> out.add(String.valueOf(o)));
            return out;
        }
        return Set.of();
    }
}

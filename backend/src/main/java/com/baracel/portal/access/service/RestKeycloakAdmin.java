package com.baracel.portal.access.service;

import com.baracel.portal.common.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "portal.keycloak.admin-enabled", havingValue = "true", matchIfMissing = true)
public class RestKeycloakAdmin implements KeycloakAdmin {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final KeycloakProperties props;
    private final RestClient http = RestClient.create();
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public RestKeycloakAdmin(KeycloakProperties props) { this.props = props; }

    private String admin(String path) { return props.baseUrl() + "/admin/realms/" + props.realm() + path; }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiry.minusSeconds(30))) return token;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        Map<String, Object> res = http.post()
                .uri(props.baseUrl() + "/realms/" + props.realm() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(MAP);
        token = (String) res.get("access_token");
        tokenExpiry = Instant.now().plusSeconds(((Number) res.getOrDefault("expires_in", 60)).longValue());
        return token;
    }

    private RestClient.RequestHeadersSpec<?> get(String path) {
        return http.get().uri(admin(path)).headers(h -> h.setBearerAuth(token()));
    }

    @Override
    public Set<String> linkedProviders(String userId) {
        Set<String> out = new HashSet<>();
        for (Map<String, Object> m : get("/users/" + userId + "/federated-identity").retrieve().body(LIST))
            out.add((String) m.get("identityProvider"));
        return out;
    }

    @Override
    public CredentialInfo credentials(String userId) {
        boolean pw = false, otp = false;
        for (Map<String, Object> m : get("/users/" + userId + "/credentials").retrieve().body(LIST)) {
            if ("password".equals(m.get("type"))) pw = true;
            if ("otp".equals(m.get("type"))) otp = true;
        }
        return new CredentialInfo(pw, otp);
    }

    @Override
    public List<IdpInfo> identityProviders() {
        List<IdpInfo> out = new ArrayList<>();
        for (Map<String, Object> m : get("/identity-provider/instances").retrieve().body(LIST))
            out.add(new IdpInfo((String) m.get("alias"), (String) m.getOrDefault("displayName", m.get("alias")),
                    !Boolean.FALSE.equals(m.get("enabled"))));
        return out;
    }

    @Override
    public void setIdentityProviderEnabled(String alias, boolean enabled) {
        Map<String, Object> idp = get("/identity-provider/instances/" + alias).retrieve().body(MAP);
        idp.put("enabled", enabled);
        http.put().uri(admin("/identity-provider/instances/" + alias)).headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(idp).retrieve().toBodilessEntity();
    }

    @Override
    public int countUsersWithRole(String role) {
        List<Map<String, Object>> users = get("/roles/" + role + "/users?briefRepresentation=true&first=0&max=10000")
                .retrieve().body(LIST);
        return users == null ? 0 : users.size();
    }

    @Override
    public int countUsers() {
        Integer n = get("/users/count").retrieve().body(Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public Set<String> userRoles(String userId) {
        Set<String> out = new HashSet<>();
        for (Map<String, Object> m : get("/users/" + userId + "/role-mappings/realm/composite").retrieve().body(LIST)) {
            String name = (String) m.get("name");
            if (PortalRoles.ALL.contains(name)) out.add(name);
        }
        return out;
    }

    @Override
    public void setUserRoles(String userId, Set<String> roles) {
        Set<String> current = new HashSet<>();
        for (Map<String, Object> m : get("/users/" + userId + "/role-mappings/realm").retrieve().body(LIST)) {
            String name = (String) m.get("name");
            if (PortalRoles.ALL.contains(name)) current.add(name);
        }
        List<Map<String, Object>> add = new ArrayList<>(), remove = new ArrayList<>();
        for (String r : roles) if (!current.contains(r)) add.add(role(r));
        for (String r : current) if (!roles.contains(r)) remove.add(role(r));
        String path = admin("/users/" + userId + "/role-mappings/realm");
        if (!add.isEmpty())
            http.post().uri(path).headers(h -> h.setBearerAuth(token())).contentType(MediaType.APPLICATION_JSON).body(add)
                    .retrieve().toBodilessEntity();
        if (!remove.isEmpty())
            http.method(org.springframework.http.HttpMethod.DELETE).uri(path).headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(remove).retrieve().toBodilessEntity();
    }

    private Map<String, Object> role(String name) {
        Map<String, Object> r = get("/roles/" + name).retrieve().body(MAP);
        Map<String, Object> slim = new HashMap<>();
        slim.put("id", r.get("id"));
        slim.put("name", r.get("name"));
        return slim;
    }

    @Override
    public String createUser(String email, String firstName, String lastName) {
        List<String> actions = List.of("VERIFY_EMAIL", "UPDATE_PASSWORD");
        Map<String, Object> body = new HashMap<>();
        body.put("username", email);
        body.put("email", email);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("enabled", true);
        body.put("requiredActions", actions);
        try {
            var res = http.post().uri(admin("/users")).headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            String location = res.getHeaders().getFirst("Location");
            String id = location.substring(location.lastIndexOf('/') + 1);
            http.put().uri(admin("/users/" + id + "/execute-actions-email?client_id=" + props.webClientId()
                            + "&redirect_uri=" + props.webRedirectUri()))
                    .headers(h -> h.setBearerAuth(token())).contentType(MediaType.APPLICATION_JSON).body(actions)
                    .retrieve().toBodilessEntity();
            return id;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) throw new ApiException(HttpStatus.CONFLICT, "user-exists",
                    "A sign-in account with this email already exists.");
            throw e;
        }
    }

    @Override
    public RealmInfo realm() {
        Map<String, Object> m = get("").retrieve().body(MAP);
        return new RealmInfo(((Number) m.getOrDefault("ssoSessionMaxLifespan", 28800)).intValue());
    }
}

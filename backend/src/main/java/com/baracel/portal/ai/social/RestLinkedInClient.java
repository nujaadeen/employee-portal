package com.baracel.portal.ai.social;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * LinkedIn over REST, following the official documentation:
 * OAuth: https://www.linkedin.com/oauth/v2/authorization and /oauth/v2/accessToken (authorization code flow).
 * Member: GET /v2/userinfo (scopes openid profile). Posting: POST /rest/posts with headers Linkedin-Version (YYYYMM) and
 * X-Restli-Protocol-Version: 2.0.0; scope w_member_social (author urn:li:person:{id}) or w_organization_social (urn:li:organization:{id});
 * 201 with the post URN in the x-restli-id header. Pages: GET /rest/organizationAcls?q=roleAssignee&role=ADMINISTRATOR&state=APPROVED.
 * Tokens are never logged.
 */
@Component
public class RestLinkedInClient implements LinkedInClient {
    private static final Logger log = LoggerFactory.getLogger(RestLinkedInClient.class);

    private final SocialProperties.LinkedIn cfg;
    private final RestClient http;

    public RestLinkedInClient(SocialProperties props) {
        this.cfg = props.linkedin();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        f.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    @Override
    public String authorizationUrl(String state, List<String> scopes) {
        return cfg.authBase() + "/oauth/v2/authorization?response_type=code&client_id=" + enc(cfg.clientId())
                + "&redirect_uri=" + enc(cfg.redirectUri()) + "&state=" + enc(state) + "&scope=" + enc(String.join(" ", scopes));
    }

    @Override
    public TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("grant_type", "authorization_code");
        f.add("code", code);
        f.add("client_id", cfg.clientId());
        f.add("client_secret", cfg.clientSecret());
        f.add("redirect_uri", cfg.redirectUri());
        return token(f);
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("grant_type", "refresh_token");
        f.add("refresh_token", refreshToken);
        f.add("client_id", cfg.clientId());
        f.add("client_secret", cfg.clientSecret());
        return token(f);
    }

    private TokenResponse token(MultiValueMap<String, String> form) {
        try {
            JsonNode n = http.post().uri(cfg.authBase() + "/oauth/v2/accessToken").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            if (n == null || !n.hasNonNull("access_token")) throw new LinkedInException(Failure.UNCLEAR, 200, "LinkedIn returned no access token");
            return new TokenResponse(n.get("access_token").asString(), n.path("expires_in").asLong(0), n.path("refresh_token").asString(null),
                    n.hasNonNull("refresh_token_expires_in") ? n.get("refresh_token_expires_in").asLong() : null, n.path("scope").asString(""));
        } catch (HttpStatusCodeException e) {
            throw fail(e);
        } catch (ResourceAccessException e) {
            throw new LinkedInException(Failure.UNCLEAR, 0, "LinkedIn did not answer in time");
        }
    }

    @Override
    public Profile profile(String accessToken) {
        try {
            JsonNode n = http.get().uri(cfg.apiBase() + "/v2/userinfo").header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class);
            if (n == null || !n.hasNonNull("sub")) throw new LinkedInException(Failure.UNCLEAR, 200, "LinkedIn returned no member id");
            return new Profile(n.get("sub").asString(), n.path("name").asString("LinkedIn member"));
        } catch (HttpStatusCodeException e) {
            throw fail(e);
        } catch (ResourceAccessException e) {
            throw new LinkedInException(Failure.UNCLEAR, 0, "LinkedIn did not answer in time");
        }
    }

    @Override
    public List<Organization> administeredOrganizations(String accessToken) {
        try {
            JsonNode n = http.get().uri(cfg.apiBase() + "/rest/organizationAcls?q=roleAssignee&role=ADMINISTRATOR&state=APPROVED")
                    .headers(h -> restli(h, accessToken)).retrieve().body(JsonNode.class);
            List<Organization> out = new ArrayList<>();
            if (n != null) for (JsonNode e : n.path("elements")) {
                String urn = e.path("organization").asString(e.path("organizationTarget").asString(""));
                if (urn.startsWith("urn:li:organization:")) out.add(new Organization(urn, organizationName(accessToken, urn)));
            }
            return out;
        } catch (HttpStatusCodeException e) {
            throw fail(e);
        } catch (ResourceAccessException e) {
            throw new LinkedInException(Failure.UNCLEAR, 0, "LinkedIn did not answer in time");
        }
    }

    private String organizationName(String token, String urn) {
        String id = urn.substring(urn.lastIndexOf(':') + 1);
        try {
            JsonNode n = http.get().uri(cfg.apiBase() + "/rest/organizations/" + id).headers(h -> restli(h, token)).retrieve().body(JsonNode.class);
            String name = n == null ? "" : n.path("localizedName").asString("");
            return name.isBlank() ? "LinkedIn page " + id : name;
        } catch (RuntimeException e) {
            return "LinkedIn page " + id;   // the name is a nicety; posting does not need it
        }
    }

    @Override
    public PostResult createPost(String accessToken, String authorUrn, String commentary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("author", authorUrn);
        body.put("commentary", commentary);
        body.put("visibility", "PUBLIC");
        body.put("distribution", Map.of("feedDistribution", "MAIN_FEED", "targetEntities", List.of(), "thirdPartyDistributionChannels", List.of()));
        body.put("lifecycleState", "PUBLISHED");
        body.put("isReshareDisabledByAuthor", false);
        try {
            var res = http.post().uri(cfg.apiBase() + "/rest/posts").headers(h -> restli(h, accessToken)).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
            String id = res.getHeaders().getFirst("x-restli-id");
            if (res.getStatusCode().value() != 201 || id == null || id.isBlank())
                throw new LinkedInException(Failure.UNCLEAR, res.getStatusCode().value(), "LinkedIn answered without a post id");
            return new PostResult(id);
        } catch (HttpStatusCodeException e) {
            throw fail(e);
        } catch (ResourceAccessException e) {
            throw new LinkedInException(Failure.UNCLEAR, 0, "LinkedIn did not answer in time");   // a timeout: the post may exist
        }
    }

    private void restli(org.springframework.http.HttpHeaders h, String token) {
        h.set("Authorization", "Bearer " + token);
        h.set("Linkedin-Version", cfg.apiVersion());
        h.set("X-Restli-Protocol-Version", "2.0.0");
    }

    /** 401 token expired; 429 rate limited; 400, 403, 422 rejected; other 4xx unknown error; 5xx unclear (it may have been posted). */
    private LinkedInException fail(HttpStatusCodeException e) {
        int s = e.getStatusCode().value();
        log.warn("LinkedIn answered {}", s);   // status only: bodies and tokens are not logged
        Failure f = s == 401 ? Failure.TOKEN_EXPIRED : s == 429 ? Failure.RATE_LIMITED
                : (s == 400 || s == 403 || s == 422) ? Failure.REJECTED : s >= 500 ? Failure.UNCLEAR : Failure.UNKNOWN_ERROR;
        return new LinkedInException(f, s, "LinkedIn answered " + s);
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }
}

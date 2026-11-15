package com.baracel.portal.ai.social;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** social.*: LinkedIn feature switches and credentials, and the key that encrypts stored tokens. */
@ConfigurationProperties("social")
public record SocialProperties(@DefaultValue LinkedIn linkedin, @DefaultValue Crypto crypto, @DefaultValue("http://localhost:5173") String frontendUrl) {

    public record LinkedIn(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("false") boolean companyEnabled,
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret,
            @DefaultValue("http://localhost:8080/api/social/linkedin/callback") String redirectUri,
            @DefaultValue("https://www.linkedin.com") String authBase,
            @DefaultValue("https://api.linkedin.com") String apiBase,
            /** The Linkedin-Version header, YYYYMM. 202511 is retired on 2026-11-16. */
            @DefaultValue("202610") String apiVersion,
            /** Optional: connect this organisation id when the member administers several. */
            @DefaultValue("") String companyOrganizationId,
            @DefaultValue("15s") java.time.Duration readTimeout) {}

    /** AES-GCM key (base64, 32 bytes) with an id so keys can be rotated. Older keys stay readable. */
    public record Crypto(@DefaultValue("") String tokenKey, @DefaultValue("k1") String tokenKeyId, @DefaultValue("") String oldKeys) {}
}

package com.baracel.portal.ai.social;

import java.util.List;

/** The LinkedIn calls the portal needs. Implemented over REST; tests run it against WireMock. */
public interface LinkedInClient {
    record TokenResponse(String accessToken, long expiresInSeconds, String refreshToken, Long refreshExpiresInSeconds, String scope) {}
    record Profile(String id, String name) {}
    record Organization(String urn, String name) {}
    record PostResult(String id) {}

    /** How a post failed. TIMEOUT and UNCLEAR mean we do not know whether it was posted. */
    enum Failure { TOKEN_EXPIRED, RATE_LIMITED, REJECTED, UNKNOWN_ERROR, UNCLEAR }

    class LinkedInException extends RuntimeException {
        private final Failure failure;
        private final int status;
        public LinkedInException(Failure failure, int status, String message) {
            super(message);
            this.failure = failure;
            this.status = status;
        }
        public Failure failure() { return failure; }
        public int status() { return status; }
    }

    String authorizationUrl(String state, List<String> scopes);
    TokenResponse exchangeCode(String code);
    TokenResponse refresh(String refreshToken);
    /** The signed-in member (OpenID Connect userinfo: sub is the member id, so the author URN is urn:li:person:{sub}). */
    Profile profile(String accessToken);
    /** Pages the member administers (organizationAcls, role ADMINISTRATOR, state APPROVED). */
    List<Organization> administeredOrganizations(String accessToken);
    /** Creates a text post. Throws LinkedInException. */
    PostResult createPost(String accessToken, String authorUrn, String commentary);
}

package com.baracel.portal.common.web;

import org.springframework.http.HttpStatus;

/** Business error that is rendered as an RFC 7807 ProblemDetail. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String type;
    private Integer retryAfterSeconds;

    public ApiException(HttpStatus status, String type, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
    }

    public HttpStatus status() { return status; }
    public String type() { return type; }
    public Integer retryAfterSeconds() { return retryAfterSeconds; }
    public ApiException retryAfter(int seconds) { this.retryAfterSeconds = seconds; return this; }

    public static ApiException badRequest(String type, String detail) { return new ApiException(HttpStatus.BAD_REQUEST, type, detail); }
    public static ApiException forbidden(String detail) { return new ApiException(HttpStatus.FORBIDDEN, "forbidden", detail); }
    public static ApiException notFound(String detail) { return new ApiException(HttpStatus.NOT_FOUND, "not-found", detail); }
    public static ApiException unavailable(String type, String detail) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, type, detail); }
    public static ApiException tooManyRequests(String detail, int retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", detail).retryAfter(retryAfterSeconds);
    }
    public static ApiException conflict(String type, String detail) { return new ApiException(HttpStatus.CONFLICT, type, detail); }
    public static ApiException pendingApproval() {
        return new ApiException(HttpStatus.FORBIDDEN, "pending-approval",
                "Your account is waiting for HR approval. Only your profile is available until then.");
    }
}

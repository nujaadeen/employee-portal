package com.baracel.portal.common.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetails {
    public static final String BASE = "https://portal.baracel.example/problems/";

    private ProblemDetails() {}

    public static ProblemDetail of(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(BASE + type));
        pd.setTitle(title(type, status));
        return pd;
    }

    private static String title(String type, HttpStatus status) {
        String t = type.replace('-', ' ');
        return t.isEmpty() ? status.getReasonPhrase() : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}

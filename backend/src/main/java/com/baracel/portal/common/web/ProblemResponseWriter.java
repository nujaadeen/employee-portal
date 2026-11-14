package com.baracel.portal.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Writes a ProblemDetail from servlet filters and security handlers, where controller advice does not apply. */
@Component
public class ProblemResponseWriter {
    private final ObjectMapper mapper;

    public ProblemResponseWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String type, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetails.of(status, type, detail);
        pd.setInstance(java.net.URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        mapper.writeValue(response.getOutputStream(), pd);
    }
}

package com.baracel.portal.identity.security;

import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.web.ProblemResponseWriter;
import com.baracel.portal.employee.domain.EmployeeStatus;
import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeView;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after the bearer token is validated. Creates the employee on the first call of a JWT sub
 * (just-in-time provisioning) and lets a PENDING_APPROVAL user reach only GET /api/me.
 */
public class ProvisioningFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "portal.currentUser";

    private final EmployeeService employees;
    private final ProblemResponseWriter problems;

    public ProvisioningFilter(EmployeeService employees, ProblemResponseWriter problems) {
        this.employees = employees;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token && auth.isAuthenticated()) {
            Jwt jwt = token.getToken();
            EmployeeView view;
            try {
                view = employees.provisionIfAbsent(jwt.getSubject(), jwt.getClaimAsString("email"),
                        first(jwt), jwt.getClaimAsString("family_name"));
            } catch (DataIntegrityViolationException raced) {
                view = employees.requireByKeycloakId(jwt.getSubject());
            }
            if (view.status() == EmployeeStatus.PENDING_APPROVAL && !isMe(request)) {
                problems.write(request, response, HttpStatus.FORBIDDEN, "pending-approval",
                        "Your account is waiting for HR approval. Only your profile is available until then.");
                return;
            }
            if (view.status() == EmployeeStatus.DEACTIVATED) {
                problems.write(request, response, HttpStatus.FORBIDDEN, "deactivated", "This account is deactivated.");
                return;
            }
            Set<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring(5)).collect(Collectors.toSet());
            Set<String> permissions = auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> !a.startsWith("ROLE_"))
                    .collect(Collectors.toSet());
            request.setAttribute(ATTRIBUTE, new CurrentUser(view.id(), view.keycloakId(), view.fullName(), view.email(),
                    view.status().name(), roles, permissions));
        }
        chain.doFilter(request, response);
    }

    private static String first(Jwt jwt) {
        String given = jwt.getClaimAsString("given_name");
        if (given != null) return given;
        String name = jwt.getClaimAsString("name");
        return name == null ? null : name.split(" ")[0];
    }

    private static boolean isMe(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "/api/me".equals(request.getRequestURI());
    }
}

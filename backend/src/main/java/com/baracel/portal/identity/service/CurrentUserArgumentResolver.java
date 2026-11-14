package com.baracel.portal.identity.service;

import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeView;
import com.baracel.portal.identity.security.ProvisioningFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controllers declare a {@link CurrentUser} parameter. */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    private final EmployeeService employees;

    public CurrentUserArgumentResolver(EmployeeService employees) { this.employees = employees; }

    @Override
    public boolean supportsParameter(MethodParameter p) { return CurrentUser.class.equals(p.getParameterType()); }

    @Override
    public Object resolveArgument(MethodParameter p, ModelAndViewContainer mav, NativeWebRequest req, WebDataBinderFactory f) {
        HttpServletRequest http = req.getNativeRequest(HttpServletRequest.class);
        Object cached = http == null ? null : http.getAttribute(ProvisioningFilter.ATTRIBUTE);
        if (cached instanceof CurrentUser cu) return cu;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        EmployeeView v = employees.requireByKeycloakId(auth.getName());
        Set<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5)).collect(Collectors.toSet());
        Set<String> perms = auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
        return new CurrentUser(v.id(), v.keycloakId(), v.fullName(), v.email(), v.status().name(), roles, perms);
    }
}

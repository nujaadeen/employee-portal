package com.baracel.portal.identity.security;

import com.baracel.portal.common.web.ProblemResponseWriter;
import com.baracel.portal.employee.service.EmployeeService;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KeycloakJwtConverter converter, EmployeeService employees,
                                            ProblemResponseWriter problems) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/integrations/gitea/webhook").permitAll()   // HMAC checked in the controller
                .requestMatchers(HttpMethod.GET, "/api/social/linkedin/callback").permitAll()       // browser redirect from LinkedIn: identified by the one-time state
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((req, res, ex) ->
                        problems.write(req, res, HttpStatus.UNAUTHORIZED, "unauthorized", "A valid access token is required."))
                .accessDeniedHandler((req, res, ex) ->
                        problems.write(req, res, HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do this.")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        problems.write(req, res, HttpStatus.UNAUTHORIZED, "unauthorized", "A valid access token is required."))
                .accessDeniedHandler((req, res, ex) ->
                        problems.write(req, res, HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do this.")))
            .addFilterAfter(new ProvisioningFilter(employees, problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${portal.cors.allowed-origins}") String origins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}

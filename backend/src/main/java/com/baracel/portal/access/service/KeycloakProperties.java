package com.baracel.portal.access.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.keycloak")
public record KeycloakProperties(String baseUrl, String realm, String clientId, String clientSecret,
                                 boolean adminEnabled, String webClientId, String webRedirectUri) {}

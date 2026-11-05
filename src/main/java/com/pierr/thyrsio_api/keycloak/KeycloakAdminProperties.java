package com.pierr.thyrsio_api.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Les propriétés "thyrsio.keycloak.admin.*" de application.properties. */
@ConfigurationProperties(prefix = "thyrsio.keycloak.admin")
public record KeycloakAdminProperties(String baseUrl, String realm, String clientId, String clientSecret) {
}
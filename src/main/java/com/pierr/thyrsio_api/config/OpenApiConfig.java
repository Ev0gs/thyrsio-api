package com.pierr.thyrsio_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "keycloak";

    @Bean
    public OpenAPI thyrsioOpenApi(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {

        // Les adresses de connexion et de token du realm Keycloak
        String oidc = issuerUri + "/protocol/openid-connect";

        return new OpenAPI()
                .info(new Info()
                        .title("Thyrsio API")
                        .version("v1")
                        .description("API REST de Thyrsio. Authentification par session : "
                                + "appeler GET /api/auth/csrf, puis POST /api/auth/login. "
                                + "Les requêtes POST, PATCH et DELETE exigent le token CSRF."))
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                .authorizationUrl(oidc + "/auth")
                                .tokenUrl(oidc + "/token")
                                .scopes(new Scopes().addString("openid", "Identité de l'utilisateur"))))))
                // Tous les endpoints utilisent ce schéma de sécurité par défaut
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));

    }
}

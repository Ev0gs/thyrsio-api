package com.pierr.thyrsio_api.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI thyrsioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Thyrsio API")
                        .version("v1")
                        .description("API REST de Thyrsio. Authentification par session : "
                                + "appeler GET /api/auth/csrf, puis POST /api/auth/login. "
                                + "Les requêtes POST, PATCH et DELETE exigent le token CSRF."))
                // La déconnexion est gérée par Spring Security, pas par un controller :
                // springdoc ne peut pas la découvrir, on la déclare à la main
                .path("/api/auth/logout", new PathItem().post(new Operation()
                        .addTagsItem("Authentification")
                        .summary("Se déconnecter")
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("Déconnecté")))));

    }
}

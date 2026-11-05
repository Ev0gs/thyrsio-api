package com.pierr.thyrsio_api.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.UUID;

public record CurrentUser(UUID keycloakId, String email, String name) {

    public static CurrentUser from(Jwt jwt)
    {
        String name = jwt.getClaimAsString("name");
        if(name == null || name.isBlank()) {
            // Si l'utilisateur n'a pas renseigné de nom : on utilise son identifiant de connexion
            name = jwt.getClaimAsString("preferred_username");
        }
        return new CurrentUser(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject())), // getSubject() = claim "sub"
                jwt.getClaimAsString("email"),
                name
        );
    }
}


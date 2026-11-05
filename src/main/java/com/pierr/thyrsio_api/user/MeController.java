package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.security.CurrentUser;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Mon compte", description = "Gestion du compte de l'utilisateur connecté")
@RestController
@RequestMapping("/api/users/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Récupérer mon profil",
            description = "Crée le profil Thyrsio au premier appel, puis le synchronise avec Keycloak.")
    @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur connecté")
    @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    @GetMapping
    public UserResponse get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = CurrentUser.from(jwt);
        return userService.syncCurrentUser(me.keycloakId(), me.name(), me.email());
    }

    @Operation(summary = "Supprimer mon compte",
            description = "Supprime le compte Keycloak et le profil Thyrsio. Le front doit ensuite déconnecter l'utilisateur.")
    @ApiResponse(responseCode = "204", description = "Compte supprimé")
    @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    @ApiResponse(responseCode = "502", description = "Keycloak ne répond pas correctement")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        userService.deleteAccount(CurrentUser.from(jwt).keycloakId());
    }
}

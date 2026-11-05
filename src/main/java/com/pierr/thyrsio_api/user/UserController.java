package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.security.CurrentUser;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Utilisateurs (admin)", description = "Gestion de tous les comptes. Réservé au rôle ADMIN.")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Récupérer un utilisateur par son id")
    @ApiResponse(responseCode = "200", description = "Utilisateur trouvé")
    @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable UUID id){
        return userService.getById(id);
    }

    @Operation(summary = "Lister les utilisateurs (paginé)")
    @ApiResponse(responseCode = "200", description = "Page d'utilisateurs")
    @ApiResponse(responseCode = "400", description = "Paramètre de tri invalide")
    @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @GetMapping
    public Page<UserResponse> getAll(@ParameterObject Pageable pageable) {
        return userService.getAll(pageable);
    }

    @Operation(summary = "Supprimer un utilisateur", description = "Supprime le compte Keycloak et le profil Thyrsio.")
    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "Utilisateur supprimé")
    @ApiResponse(responseCode = "400", description = "Un administrateur ne peut pas se supprimer via cette route")
    @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    @ApiResponse(responseCode = "502", description = "Keycloak ne répond pas correctement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
                       @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        userService.delete(id, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Donner le rôle ADMIN")
    @ApiResponse(responseCode = "204", description = "Rôle attribué")
    @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    @ApiResponse(responseCode = "502", description = "Keycloak ne répond pas correctement")
    @PutMapping("/{id}/roles/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantAdmin(@PathVariable UUID id) {
        userService.grantAdmin(id);
    }

    @Operation(summary = "Retirer le rôle ADMIN")
    @ApiResponse(responseCode = "204", description = "Rôle retiré")
    @ApiResponse(responseCode = "400", description = "Un administrateur ne peut pas retirer son propre rôle")
    @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    @ApiResponse(responseCode = "502", description = "Keycloak ne répond pas correctement")
    @DeleteMapping("/{id}/roles/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAdmin(@PathVariable UUID id,
                            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        userService.revokeAdmin(id, CurrentUser.from(jwt).keycloakId());
    }
}

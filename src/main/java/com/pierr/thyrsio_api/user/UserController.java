package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.user.dto.UpdateUserRequest;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable UUID id){
        return userService.getById(id);
    }

    @Operation(summary = "Lister les utilisateurs (paginé)")
    @ApiResponse(responseCode = "200", description = "Page d'utilisateurs")
    @ApiResponse(responseCode = "400", description = "Paramètre de tri invalide")
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @GetMapping
    public Page<UserResponse> getAll(@ParameterObject Pageable pageable) {
        return userService.getAll(pageable);
    }

    @Operation(summary = "Modifier un utilisateur")
    @ApiResponse(responseCode = "200", description = "Utilisateur modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    @ApiResponse(responseCode = "409", description = "Email déjà utilisé")
    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request){
        return userService.update(id, request);
    }

    @Operation(summary = "Supprimer un utilisateur")
    @ApiResponse(responseCode = "204", description = "Utilisateur supprimé")
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}

package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.security.AppUserPrincipal;
import com.pierr.thyrsio_api.user.dto.UpdateUserRequest;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Mon compte", description = "Gestion du compte de l'utilisateur connecté")
@RestController
@RequestMapping("/api/users/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Récupérer mon profil")
    @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur connecté")
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @GetMapping
    public UserResponse get(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal me) {
        return userService.getById(me.id());
    }

    @Operation(summary = "Modifier mon profil")
    @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur connecté")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @ApiResponse(responseCode = "403", description = "Token CSRF manquant")
    @ApiResponse(responseCode = "409", description = "Email déjà utilisé")
    @PatchMapping
    public UserResponse udpate(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal me,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(me.id(), request);
    }

    @Operation(summary = "Supprimer mon compte", description = "Supprime le compte et ferme la session.")
    @ApiResponse(responseCode = "204", description = "Compte supprimé")
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @ApiResponse(responseCode = "403", description = "Token CSRF manquant")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal me,
                       HttpServletRequest request) {
        userService.delete(me.id());

        HttpSession session = request.getSession(false);
        if(session != null)
            session.invalidate();
        SecurityContextHolder.clearContext();
    }
}

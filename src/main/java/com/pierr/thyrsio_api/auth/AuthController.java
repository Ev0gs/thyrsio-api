package com.pierr.thyrsio_api.auth;

import com.pierr.thyrsio_api.auth.dto.LoginRequest;
import com.pierr.thyrsio_api.auth.dto.RegisterRequest;
import com.pierr.thyrsio_api.security.AppUserPrincipal;
import com.pierr.thyrsio_api.user.UserService;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentification", description = "Inscription, connexion et déconnexion")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserService userService;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository, UserService userService) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userService = userService;
    }

    @Operation(summary = "Créer un compte")
    @ApiResponse(responseCode = "201", description = "Compte créé")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Email déjà utilisé")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Se connecter", description = "Ouvre une session : le navigateur reçoit le cookie JSESSIONID.")
    @PostMapping("/login")
    @ApiResponse(responseCode = "200", description = "Connecté, renvoie le profil")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "401", description = "Identifiants invalides")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpServletRequest,
                              HttpServletResponse httpServletResponse)
    {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
        );

        if(httpServletRequest.getSession(false) != null) {
            httpServletRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, httpServletRequest, httpServletResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return userService.getById(principal.id());
    }

    @Operation(summary = "Obtenir le token CSRF", description = "Dépose le cookie XSRF-TOKEN. À appeler avant toute requête POST, PATCH ou DELETE.")
    @ApiResponse(responseCode = "204", description = "Cookie XSRF-TOKEN déposé")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
        // Lire le token force Spring à le générer et à l'envoyer dans le cookie XSRF-TOKEN
        csrfToken.getToken();
    }
}
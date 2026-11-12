package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.production.dto.BottlingRequest;
import com.pierr.thyrsio_api.production.dto.BottlingResponse;
import com.pierr.thyrsio_api.production.dto.BottlingRow;
import com.pierr.thyrsio_api.production.dto.CloseBottlingRequest;
import com.pierr.thyrsio_api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Production — Mises en bouteille")
@RestController
@RequestMapping("/api/production/bottlings")
public class BottlingController {

    private final BottlingService bottlingService;

    public BottlingController(BottlingService bottlingService) {
        this.bottlingService = bottlingService;
    }

    @Operation(summary = "Lister les mises (planning)",
            description = "from / to (AAAA-MM-JJ, inclus) : les mises qui touchent cette période. "
                    + "Chaque mise planifiée indique ses manquants et si elle est en alerte (manquants à moins de 10 jours).")
    @GetMapping
    public Page<BottlingRow> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BottlingStatus status,
            @ParameterObject @PageableDefault(size = 100, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return bottlingService.list(from, to, status, pageable);
    }

    @Operation(summary = "Fiche d'une mise", description = "Avec ses matières : stock, réservé par les mises précédentes, manquant.")
    @ApiResponse(responseCode = "404", description = "Mise introuvable")
    @GetMapping("/{id}")
    public BottlingResponse get(@PathVariable UUID id) {
        return bottlingService.get(id);
    }

    @Operation(summary = "Planifier une mise")
    @ApiResponse(responseCode = "201", description = "Mise planifiée (code MB-26-001...)")
    @ApiResponse(responseCode = "400", description = "Données invalides : dates, produit fini, cuve, matières")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BottlingResponse create(@Valid @RequestBody BottlingRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return bottlingService.create(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Modifier une mise planifiée", description = "Remplace le planning et la liste des matières.")
    @ApiResponse(responseCode = "409", description = "Mise plus planifiée, ou modifiée entre-temps")
    @PutMapping("/{id}")
    public BottlingResponse update(@PathVariable UUID id, @Valid @RequestBody BottlingRequest request) {
        return bottlingService.update(id, request);
    }

    @Operation(summary = "Annuler une mise planifiée", description = "Aucun effet sur le stock ni sur les cuves.")
    @ApiResponse(responseCode = "409", description = "Mise plus planifiée")
    @PostMapping("/{id}/cancel")
    public BottlingResponse cancel(@PathVariable UUID id) {
        return bottlingService.cancel(id);
    }

    @Operation(summary = "Clôturer une mise avec le réel",
            description = "Consomme les matières sèches saisies, tire le vin de la cuve (mise chez nous), "
                    + "crée le lot des bouteilles produites et les fait entrer en stock. Tout ou rien.")
    @ApiResponse(responseCode = "400", description = "Données invalides (lot, décimales, vin)")
    @ApiResponse(responseCode = "409", description = "Stock ou vin insuffisant, mise pas encore commencée ou plus planifiée")
    @PostMapping("/{id}/close")
    public BottlingResponse close(@PathVariable UUID id, @Valid @RequestBody CloseBottlingRequest request,
                                  @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return bottlingService.close(id, request, CurrentUser.from(jwt).keycloakId());
    }
}

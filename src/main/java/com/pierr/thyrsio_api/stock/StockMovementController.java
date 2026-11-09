package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.security.CurrentUser;
import com.pierr.thyrsio_api.stock.dto.CreateMovementRequest;
import com.pierr.thyrsio_api.stock.dto.MovementResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Stock — Mouvements")
@RestController
@RequestMapping("/api/stock/movements")
public class StockMovementController {

    private final StockService stockService;

    public StockMovementController(StockService stockService) {
        this.stockService = stockService;
    }

    @Operation(summary = "Saisir un mouvement de stock",
            description = "Types manuels : RECEPTION, CONSUMPTION, LOSS (quantité positive) et ADJUSTMENT (quantité signée). "
                    + "Un mouvement ne se modifie pas : pour corriger, saisir un ADJUSTMENT inverse.")
    @ApiResponse(responseCode = "201", description = "Mouvement enregistré, solde mis à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides (lot, décimales, type non manuel...)")
    @ApiResponse(responseCode = "409", description = "Stock insuffisant ou article archivé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse create(@Valid @RequestBody CreateMovementRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return stockService.recordManual(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Historique des mouvements (paginé, filtrable)",
            description = "Du plus récent au plus ancien. from est inclus, to est exclu (ISO-8601, ex. 2026-11-01T00:00:00Z).")
    @GetMapping
    public Page<MovementResponse> list(
            @RequestParam(required = false) UUID articleId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return stockService.listMovements(articleId, lotId, type, from, to, pageable);
    }
}
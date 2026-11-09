package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.security.CurrentUser;
import com.pierr.thyrsio_api.stock.dto.*;
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

import java.util.UUID;

@Tag(name = "Stock — Inventaires")
@RestController
@RequestMapping("/api/stock/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "Lister les inventaires", description = "Du plus récent au plus ancien, filtrable par statut.")
    @GetMapping
    public Page<InventorySummaryResponse> list(
            @RequestParam(required = false) InventoryStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.list(status, pageable);
    }

    @Operation(summary = "Détail d'un inventaire, avec ses lignes",
            description = "En brouillon, le stock théorique et l'écart sont calculés en direct.")
    @ApiResponse(responseCode = "404", description = "Inventaire introuvable")
    @GetMapping("/{id}")
    public InventoryResponse get(@PathVariable UUID id) {
        return inventoryService.get(id);
    }

    @Operation(summary = "Créer un inventaire (brouillon)",
            description = "prefill=true crée une ligne à compter pour chaque stock non nul.")
    @ApiResponse(responseCode = "201", description = "Inventaire créé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse create(@Valid @RequestBody CreateInventoryRequest request,
                                    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return inventoryService.create(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Ajouter une ligne", description = "Par exemple un lot retrouvé qui n'était pas dans le stock théorique.")
    @ApiResponse(responseCode = "201", description = "Ligne ajoutée")
    @ApiResponse(responseCode = "400", description = "Article, lot ou décimales invalides")
    @ApiResponse(responseCode = "409", description = "Ligne déjà présente, ou inventaire plus en brouillon")
    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse addLine(@PathVariable UUID id, @Valid @RequestBody AddInventoryLineRequest request) {
        return inventoryService.addLine(id, request);
    }

    @Operation(summary = "Saisir le comptage d'une ligne")
    @ApiResponse(responseCode = "409", description = "Inventaire plus en brouillon, ou modifié entre-temps")
    @PutMapping("/{id}/lines/{lineId}")
    public InventoryResponse countLine(@PathVariable UUID id, @PathVariable UUID lineId,
                                       @Valid @RequestBody CountInventoryLineRequest request) {
        return inventoryService.countLine(id, lineId, request);
    }

    @Operation(summary = "Retirer une ligne d'un brouillon")
    @ApiResponse(responseCode = "204", description = "Ligne retirée")
    @DeleteMapping("/{id}/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        inventoryService.removeLine(id, lineId);
    }

    @Operation(summary = "Valider l'inventaire",
            description = "Crée un mouvement INVENTORY pour chaque écart et fige le résultat. Toutes les lignes doivent être comptées.")
    @ApiResponse(responseCode = "409", description = "Lignes non comptées, inventaire plus en brouillon, ou modifié entre-temps")
    @PostMapping("/{id}/validate")
    public InventoryResponse validate(@PathVariable UUID id,
                                      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return inventoryService.validate(id, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Annuler l'inventaire", description = "Aucun effet sur le stock.")
    @ApiResponse(responseCode = "409", description = "Inventaire plus en brouillon")
    @PostMapping("/{id}/cancel")
    public InventoryResponse cancel(@PathVariable UUID id) {
        inventoryService.cancel(id);
        return inventoryService.get(id);
    }
}
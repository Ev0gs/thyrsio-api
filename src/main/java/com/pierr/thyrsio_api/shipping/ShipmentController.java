package com.pierr.thyrsio_api.shipping;

import com.pierr.thyrsio_api.security.CurrentUser;
import com.pierr.thyrsio_api.shipping.dto.ShipmentRequest;
import com.pierr.thyrsio_api.shipping.dto.ShipmentResponse;
import com.pierr.thyrsio_api.shipping.dto.ShipmentRow;
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

@Tag(name = "Expéditions")
@RestController
@RequestMapping("/api/shipping/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Operation(summary = "Lister les expéditions (paginé)", description = "from / to : date prévue, incluses.")
    @GetMapping
    public Page<ShipmentRow> list(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 25, sort = "plannedDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return shipmentService.list(status, orderId, from, to, pageable);
    }

    @Operation(summary = "Fiche d'une expédition", description = "Avec tout ce qu'il faut pour le bon de livraison.")
    @ApiResponse(responseCode = "404", description = "Expédition introuvable")
    @GetMapping("/{id}")
    public ShipmentResponse get(@PathVariable UUID id) {
        return shipmentService.get(id);
    }

    @Operation(summary = "Préparer une expédition", description = "Une commande confirmée ; des lignes avec leur lot. Rien ne sort du stock.")
    @ApiResponse(responseCode = "201", description = "Expédition préparée (code EX-26-001...)")
    @ApiResponse(responseCode = "409", description = "Commande pas à expédier, ou plus de bouteilles que le reste dû")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentResponse create(@Valid @RequestBody ShipmentRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return shipmentService.create(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Modifier une expédition en préparation", description = "Les lignes sont remplacées.")
    @PutMapping("/{id}")
    public ShipmentResponse update(@PathVariable UUID id, @Valid @RequestBody ShipmentRequest request) {
        return shipmentService.update(id, request);
    }

    @Operation(summary = "Annuler une expédition en préparation")
    @PostMapping("/{id}/cancel")
    public ShipmentResponse cancel(@PathVariable UUID id) {
        return shipmentService.cancel(id);
    }

    @Operation(summary = "Valider le départ",
            description = "Sort les bouteilles du stock lot par lot, fait avancer la commande, fige le bon de livraison. Tout ou rien.")
    @ApiResponse(responseCode = "409", description = "Stock insuffisant, commande plus à expédier, ou modifiée entre-temps")
    @PostMapping("/{id}/ship")
    public ShipmentResponse ship(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return shipmentService.ship(id, CurrentUser.from(jwt).keycloakId());
    }
}

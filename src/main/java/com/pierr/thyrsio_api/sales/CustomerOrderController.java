package com.pierr.thyrsio_api.sales;

import com.pierr.thyrsio_api.sales.dto.*;
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
import java.util.List;
import java.util.UUID;

@Tag(name = "Ventes — Commandes clients")
@RestController
@RequestMapping("/api/sales")
public class CustomerOrderController {

    private final CustomerOrderService orderService;

    public CustomerOrderController(CustomerOrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Lister les commandes (paginé)",
            description = "open=true : confirmées ou expédiées en partie. loadingTo (AAAA-MM-JJ) : à charger au plus tard ce jour. "
                    + "q cherche dans le code et la référence du client.")
    @GetMapping("/orders")
    public Page<OrderRow> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "false") boolean open,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate loadingTo,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 25, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.list(status, open, clientId, loadingTo, q, pageable);
    }

    @Operation(summary = "Fiche d'une commande",
            description = "Avec, pour chaque ligne : ce que couvrent le stock et les mises, et ce qui reste à planifier.")
    @ApiResponse(responseCode = "404", description = "Commande introuvable")
    @GetMapping("/orders/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }

    @Operation(summary = "Créer une commande (brouillon)",
            description = "Adresse de livraison et incoterm absents : ceux du client. Seuls des produits finis se commandent.")
    @ApiResponse(responseCode = "201", description = "Commande créée (code CC-26-001...)")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                                @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return orderService.create(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Modifier une commande", description = "Brouillon, ou confirmée sans rien d'expédié. Les lignes sont remplacées.")
    @ApiResponse(responseCode = "409", description = "Commande plus modifiable, ligne produite par une mise, ou modifiée entre-temps")
    @PutMapping("/orders/{id}")
    public OrderResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOrderRequest request) {
        return orderService.update(id, request);
    }

    @Operation(summary = "Confirmer une commande", description = "Elle compte alors dans la couverture et peut être expédiée.")
    @ApiResponse(responseCode = "409", description = "Pas un brouillon, ou aucune ligne")
    @PostMapping("/orders/{id}/confirm")
    public OrderResponse confirm(@PathVariable UUID id) {
        return orderService.confirm(id);
    }

    @Operation(summary = "Annuler une commande", description = "Seulement si rien n'est encore parti.")
    @ApiResponse(responseCode = "409", description = "Déjà expédiée en partie, ou expédition en préparation")
    @PostMapping("/orders/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return orderService.cancel(id);
    }

    @Operation(summary = "Lignes à planifier",
            description = "Les lignes confirmées que ni le stock ni les mises planifiées ne couvrent, de la plus urgente à la moins urgente.")
    @GetMapping("/order-lines/to-plan")
    public List<LineToPlan> linesToPlan() {
        return orderService.linesToPlan();
    }
}

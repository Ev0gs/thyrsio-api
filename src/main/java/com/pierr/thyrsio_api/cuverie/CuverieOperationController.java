package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.cuverie.dto.ReceptionRequest;
import com.pierr.thyrsio_api.cuverie.dto.TankMovementResponse;
import com.pierr.thyrsio_api.cuverie.dto.TransferRequest;
import com.pierr.thyrsio_api.cuverie.dto.WineLotResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Cuverie — Mouvements")
@RestController
@RequestMapping("/api/cuverie")
public class CuverieOperationController {

    private final CuverieService cuverieService;

    public CuverieOperationController(CuverieService cuverieService) {
        this.cuverieService = cuverieService;
    }

    @Operation(summary = "Réceptionner du vin en vrac",
            description = "Crée le lot de vin (code V26-001...) et remplit une ou plusieurs cuves. Tout ou rien.")
    @ApiResponse(responseCode = "201", description = "Lot créé, cuves remplies")
    @ApiResponse(responseCode = "400", description = "Données invalides, fournisseur inconnu ou pas fournisseur de vin")
    @ApiResponse(responseCode = "409", description = "Capacité dépassée, cuve occupée par un autre vin, en nettoyage...")
    @PostMapping("/receptions")
    @ResponseStatus(HttpStatus.CREATED)
    public WineLotResponse receive(@Valid @RequestBody ReceptionRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return cuverieService.receive(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Transférer du vin d'une cuve à une autre",
            description = "volumeHl : ce qui arrive dans la destination. lossHl : la perte en route. "
                    + "emptySource=true vide entièrement la cuve d'origine. Renvoie les mouvements créés (même transferId).")
    @ApiResponse(responseCode = "201", description = "Transfert enregistré")
    @ApiResponse(responseCode = "409", description = "Volume insuffisant, destination occupée par un autre vin ou trop petite")
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TankMovementResponse> transfer(@Valid @RequestBody TransferRequest request,
                                               @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return cuverieService.transfer(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Historique des mouvements de vin (paginé, filtrable)",
            description = "Du plus récent au plus ancien. from est inclus, to est exclu (ISO-8601).")
    @GetMapping("/movements")
    public Page<TankMovementResponse> movements(
            @RequestParam(required = false) UUID tankId,
            @RequestParam(required = false) UUID wineLotId,
            @RequestParam(required = false) TankMovementType type,
            @RequestParam(required = false) UUID transferId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return cuverieService.listMovements(tankId, wineLotId, type, transferId, from, to, pageable);
    }
}

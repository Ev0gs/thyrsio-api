package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.cuverie.dto.*;
import com.pierr.thyrsio_api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Cuverie — Cuves")
@RestController
@RequestMapping("/api/cuverie/tanks")
public class TankController {

    private final TankService tankService;
    private final CuverieService cuverieService;

    public TankController(TankService tankService, CuverieService cuverieService) {
        this.tankService = tankService;
        this.cuverieService = cuverieService;
    }

    @Operation(summary = "Lister les cuves, avec leur contenu",
            description = "Triées par code. Non paginé. Les cuves archivées sont exclues, sauf avec includeArchived=true.")
    @GetMapping
    public List<TankResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return tankService.list(includeArchived);
    }

    @Operation(summary = "Récupérer une cuve")
    @ApiResponse(responseCode = "404", description = "Cuve introuvable")
    @GetMapping("/{id}")
    public TankResponse get(@PathVariable UUID id) {
        return tankService.get(id);
    }

    @Operation(summary = "Créer une cuve", description = "Le code est mis en majuscules. La cuve est créée vide et en service.")
    @ApiResponse(responseCode = "201", description = "Cuve créée")
    @ApiResponse(responseCode = "409", description = "Code déjà utilisé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TankResponse create(@Valid @RequestBody CreateTankRequest request) {
        return tankService.create(request);
    }

    @Operation(summary = "Modifier une cuve", description = "Libellé et contenance. Le code ne change pas.")
    @ApiResponse(responseCode = "409", description = "Contenance inférieure au volume présent")
    @PutMapping("/{id}")
    public TankResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTankRequest request) {
        return tankService.update(id, request);
    }

    @Operation(summary = "Changer le statut d'une cuve",
            description = "IN_SERVICE, CLEANING ou OUT_OF_SERVICE. Vide / occupée se calcule seul. "
                    + "Une cuve doit être vide pour passer en nettoyage ou hors service.")
    @ApiResponse(responseCode = "409", description = "La cuve contient du vin")
    @PutMapping("/{id}/status")
    public TankResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody TankStatusRequest request) {
        return tankService.changeStatus(id, request.status());
    }

    @Operation(summary = "Archiver une cuve", description = "Elle doit être vide. Son historique reste consultable.")
    @ApiResponse(responseCode = "204", description = "Cuve archivée")
    @ApiResponse(responseCode = "409", description = "La cuve contient du vin")
    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        tankService.archive(id);
    }

    @Operation(summary = "Réactiver une cuve")
    @ApiResponse(responseCode = "204", description = "Cuve réactivée")
    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID id) {
        tankService.reactivate(id);
    }

    @Operation(summary = "Enregistrer une perte", description = "Évaporation, fuite, lies retirées... Volume positif.")
    @ApiResponse(responseCode = "201", description = "Perte enregistrée, volume de la cuve mis à jour")
    @ApiResponse(responseCode = "409", description = "Cuve vide ou volume insuffisant")
    @PostMapping("/{id}/losses")
    @ResponseStatus(HttpStatus.CREATED)
    public TankMovementResponse recordLoss(@PathVariable UUID id, @Valid @RequestBody LossRequest request,
                                           @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return cuverieService.recordLoss(id, request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Saisir un jaugeage",
            description = "Le volume réellement mesuré : l'écart avec le volume enregistré devient un mouvement ADJUSTMENT.")
    @ApiResponse(responseCode = "201", description = "Ajustement enregistré")
    @ApiResponse(responseCode = "409", description = "Aucun écart, ou capacité dépassée")
    @PostMapping("/{id}/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    public TankMovementResponse measure(@PathVariable UUID id, @Valid @RequestBody MeasureRequest request,
                                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return cuverieService.measure(id, request, CurrentUser.from(jwt).keycloakId());
    }
}

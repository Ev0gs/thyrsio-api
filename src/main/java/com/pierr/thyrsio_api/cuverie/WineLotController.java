package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.cuverie.dto.AnalysisRequest;
import com.pierr.thyrsio_api.cuverie.dto.AnalysisResponse;
import com.pierr.thyrsio_api.cuverie.dto.UpdateWineLotRequest;
import com.pierr.thyrsio_api.cuverie.dto.WineLotResponse;
import com.pierr.thyrsio_api.cuverie.dto.WineLotRow;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Cuverie — Lots de vin")
@RestController
@RequestMapping("/api/cuverie")
public class WineLotController {

    private final WineLotService wineLotService;
    private final WineAnalysisService analysisService;

    public WineLotController(WineLotService wineLotService, WineAnalysisService analysisService) {
        this.wineLotService = wineLotService;
        this.analysisService = analysisService;
    }

    @Operation(summary = "Lister les lots de vin (paginé)",
            description = "Du plus récent au plus ancien. inTank=true : seulement les lots encore en cuve. "
                    + "q cherche dans le code, l'appellation et le n° de lot du vendeur.")
    @GetMapping("/wine-lots")
    public Page<WineLotRow> list(
            @RequestParam(defaultValue = "false") boolean inTank,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID supplierId,
            @ParameterObject @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return wineLotService.list(inTank, q, supplierId, pageable);
    }

    @Operation(summary = "Fiche d'un lot de vin, avec les cuves qui le contiennent")
    @ApiResponse(responseCode = "404", description = "Lot introuvable")
    @GetMapping("/wine-lots/{id}")
    public WineLotResponse get(@PathVariable UUID id) {
        return wineLotService.get(id);
    }

    @Operation(summary = "Corriger la fiche d'un lot",
            description = "Le code, le fournisseur et la date de réception ne changent pas.")
    @PutMapping("/wine-lots/{id}")
    public WineLotResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateWineLotRequest request) {
        return wineLotService.update(id, request);
    }

    @Operation(summary = "Analyses d'un lot", description = "De la plus récente à la plus ancienne.")
    @GetMapping("/wine-lots/{id}/analyses")
    public List<AnalysisResponse> analyses(@PathVariable UUID id) {
        return analysisService.list(id);
    }

    @Operation(summary = "Ajouter une analyse",
            description = "Valeurs facultatives (degré, SO₂ libre et total, acidités), au moins une valeur ou une note.")
    @ApiResponse(responseCode = "201", description = "Analyse ajoutée")
    @PostMapping("/wine-lots/{id}/analyses")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse addAnalysis(@PathVariable UUID id, @Valid @RequestBody AnalysisRequest request,
                                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return analysisService.add(id, request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Supprimer une analyse saisie par erreur")
    @ApiResponse(responseCode = "204", description = "Analyse supprimée")
    @DeleteMapping("/analyses/{analysisId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnalysis(@PathVariable UUID analysisId) {
        analysisService.delete(analysisId);
    }
}

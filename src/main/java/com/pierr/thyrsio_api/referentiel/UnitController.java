package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.referentiel.dto.CreateUnitRequest;
import com.pierr.thyrsio_api.referentiel.dto.UnitResponse;
import com.pierr.thyrsio_api.referentiel.dto.UpdateUnitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Référentiel — Unités")
@RestController
@RequestMapping("/api/referentiel/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @Operation(summary = "Lister les unités")
    @GetMapping
    public List<UnitResponse> list() {
        return unitService.list();
    }

    @Operation(summary = "Récupérer une unité")
    @ApiResponse(responseCode = "200", description = "Unité trouvée")
    @ApiResponse(responseCode = "404", description = "Unité introuvable")
    @GetMapping("/{id}")
    public UnitResponse get(@PathVariable UUID id) {
        return unitService.get(id);
    }

    @Operation(summary = "Créer une unité")
    @ApiResponse(responseCode = "201", description = "Unité créée")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Code déjà utilisé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse create(@Valid @RequestBody CreateUnitRequest request) {
        return unitService.create(request);
    }

    @Operation(summary = "Modifier une unité")
    @ApiResponse(responseCode = "200", description = "Unité modifiée")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "404", description = "Unité introuvable")
    @PutMapping("/{id}")
    public UnitResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateUnitRequest request) {
        return unitService.update(id, request);
    }
}

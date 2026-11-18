package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.referentiel.dto.AppellationRequest;
import com.pierr.thyrsio_api.referentiel.dto.AppellationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Référentiel — Appellations", description = "Leur sigle entre dans les numéros de lot (LBX2401)")
@RestController
@RequestMapping("/api/referentiel/appellations")
public class AppellationController {

    private final AppellationService service;

    public AppellationController(AppellationService service) {
        this.service = service;
    }

    @Operation(summary = "Lister les appellations", description = "Actives seulement, sauf includeArchived=true.")
    @GetMapping
    public List<AppellationResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(includeArchived);
    }

    @Operation(summary = "Créer une appellation")
    @ApiResponse(responseCode = "409", description = "Sigle ou nom déjà utilisé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppellationResponse create(@Valid @RequestBody AppellationRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Modifier une appellation",
            description = "Les numéros de lot déjà attribués ne changent pas ; les suivants prennent le nouveau sigle.")
    @PutMapping("/{id}")
    public AppellationResponse update(@PathVariable UUID id, @Valid @RequestBody AppellationRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Archiver une appellation")
    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        service.archive(id);
    }

    @Operation(summary = "Réactiver une appellation")
    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable UUID id) {
        service.restore(id);
    }
}

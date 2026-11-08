package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.referentiel.dto.SupplierRequest;
import com.pierr.thyrsio_api.referentiel.dto.SupplierResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Référentiel — Fournisseurs")
@RestController
@RequestMapping("/api/referentiel/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Operation(summary = "Lister les fournisseurs (paginé)", description = "Les fournisseurs archivés sont exclus, sauf avec includeArchived=true.")
    @GetMapping
    public Page<SupplierResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived,
                                       @ParameterObject Pageable pageable) {
        return supplierService.list(includeArchived, pageable);
    }

    @Operation(summary = "Récupérer un fournisseur")
    @ApiResponse(responseCode = "200", description = "Fournisseur trouvé")
    @ApiResponse(responseCode = "404", description = "Fournisseur introuvable")
    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable UUID id) {
        return supplierService.get(id);
    }

    @Operation(summary = "Créer un fournisseur")
    @ApiResponse(responseCode = "201", description = "Fournisseur créé")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @Operation(summary = "Modifier un fournisseur", description = "Remplace la fiche entière : un champ facultatif vide efface la valeur.")
    @ApiResponse(responseCode = "200", description = "Fournisseur modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "404", description = "Fournisseur introuvable")
    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @Operation(summary = "Archiver un fournisseur")
    @ApiResponse(responseCode = "204", description = "Fournisseur archivé")
    @ApiResponse(responseCode = "404", description = "Fournisseur introuvable")
    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        supplierService.archive(id);
    }

    @Operation(summary = "Réactiver un fournisseur")
    @ApiResponse(responseCode = "204", description = "Fournisseur réactivé")
    @ApiResponse(responseCode = "404", description = "Fournisseur introuvable")
    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID id) {
        supplierService.reactivate(id);
    }
}

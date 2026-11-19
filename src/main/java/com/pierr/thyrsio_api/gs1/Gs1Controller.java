package com.pierr.thyrsio_api.gs1;

import com.pierr.thyrsio_api.gs1.dto.*;
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

@Tag(name = "Gencodes", description = "Les préfixes GS1 du client et les gencodes (EAN-13) de ses produits finis")
@RestController
@RequestMapping("/api/gs1")
public class Gs1Controller {

    private final Gs1Service service;

    public Gs1Controller(Gs1Service service) {
        this.service = service;
    }

    // ---------- Préfixes ----------

    @Operation(summary = "Les préfixes GS1", description = "Avec leur remplissage : gencodes possibles, pris, restants.")
    @GetMapping("/prefixes")
    public List<PrefixResponse> prefixes() {
        return service.listPrefixes();
    }

    @Operation(summary = "Ajouter un préfixe GS1")
    @ApiResponse(responseCode = "409", description = "Recouvre un préfixe existant")
    @PostMapping("/prefixes")
    @ResponseStatus(HttpStatus.CREATED)
    public PrefixResponse createPrefix(@Valid @RequestBody PrefixRequest request) {
        return service.createPrefix(request);
    }

    @Operation(summary = "Renommer, activer ou désactiver un préfixe")
    @PutMapping("/prefixes/{id}")
    public PrefixResponse updatePrefix(@PathVariable UUID id, @Valid @RequestBody PrefixRequest request) {
        return service.updatePrefix(id, request);
    }

    // ---------- Gencodes ----------

    @Operation(summary = "Les gencodes", description = "Filtrés par produit fini, conditionnement, statut ou texte "
            + "(chiffres du gencode, libellé, produit). Les plus récents d'abord.")
    @GetMapping("/gtins")
    public Page<GtinResponse> list(@RequestParam(required = false) UUID articleId,
                                   @RequestParam(required = false) GtinPackaging packaging,
                                   @RequestParam(required = false) GtinStatus status,
                                   @RequestParam(required = false) String q,
                                   @ParameterObject @PageableDefault(size = 50, sort = "createdAt",
                                           direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(articleId, packaging, status, q, pageable);
    }

    @GetMapping("/gtins/{id}")
    public GtinResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Générer le gencode suivant et l'attribuer à un produit fini",
            description = "Préfixe + référence suivante + clé de contrôle. Une bouteille, ou un groupé (carton de 6, "
                    + "caisse bois de 6...). Un seul gencode actif par produit et par conditionnement.")
    @ApiResponse(responseCode = "409", description = "Préfixe plein ou inactif, conditionnement déjà codifié")
    @PostMapping("/gtins/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public GtinResponse generate(@Valid @RequestBody GenerateGtinRequest request,
                                 @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.generate(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Enregistrer un gencode existant", description = "Créé avant Thyrsio : sa clé est vérifiée.")
    @ApiResponse(responseCode = "400", description = "Pas un EAN-13 valide")
    @ApiResponse(responseCode = "409", description = "Déjà enregistré")
    @PostMapping("/gtins")
    @ResponseStatus(HttpStatus.CREATED)
    public GtinResponse record(@Valid @RequestBody RecordGtinRequest request,
                               @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.record(request, CurrentUser.from(jwt).keycloakId());
    }

    @Operation(summary = "Attribuer un gencode réservé à un produit fini")
    @PostMapping("/gtins/{id}/assign")
    public GtinResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignGtinRequest request) {
        return service.assign(id, request);
    }

    @Operation(summary = "Modifier le libellé et les notes d'un gencode")
    @PutMapping("/gtins/{id}")
    public GtinResponse describe(@PathVariable UUID id, @Valid @RequestBody DescribeGtinRequest request) {
        return service.describe(id, request);
    }

    @Operation(summary = "Retirer un gencode", description = "Il ne désigne plus rien et ne sera jamais redonné.")
    @PostMapping("/gtins/{id}/retire")
    public GtinResponse retire(@PathVariable UUID id) {
        return service.retire(id);
    }
}

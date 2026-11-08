package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.stock.dto.CreateLotRequest;
import com.pierr.thyrsio_api.stock.dto.LotResponse;
import com.pierr.thyrsio_api.stock.dto.UpdateLotRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Stock — Lots")
@RestController
@RequestMapping("/api/stock/lots")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @Operation(summary = "Lister les lots d'un article", description = "Du plus récent au plus ancien.")
    @ApiResponse(responseCode = "200", description = "Lots de l'article")
    @ApiResponse(responseCode = "400", description = "Article inconnu")
    @GetMapping
    public List<LotResponse> listByArticle(@RequestParam UUID articleId) {
        return lotService.listByArticle(articleId);
    }

    @Operation(summary = "Récupérer un lot")
    @ApiResponse(responseCode = "200", description = "Lot trouvé")
    @ApiResponse(responseCode = "404", description = "Lot introuvable")
    @GetMapping("/{id}")
    public LotResponse get(@PathVariable UUID id) {
        return lotService.get(id);
    }

    @Operation(summary = "Créer un lot",
            description = "Réservé aux articles suivis par lot. En temps normal, les lots seront créés par les réceptions.")
    @ApiResponse(responseCode = "201", description = "Lot créé")
    @ApiResponse(responseCode = "400", description = "Données invalides, article inconnu, archivé ou non suivi par lot")
    @ApiResponse(responseCode = "409", description = "Numéro de lot déjà utilisé pour cet article")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LotResponse create(@Valid @RequestBody CreateLotRequest request) {
        return lotService.create(request);
    }

    @Operation(summary = "Modifier un lot", description = "Date de péremption et notes. L'article et le numéro sont définitifs.")
    @ApiResponse(responseCode = "200", description = "Lot modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "404", description = "Lot introuvable")
    @PutMapping("/{id}")
    public LotResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateLotRequest request) {
        return lotService.update(id, request);
    }
}
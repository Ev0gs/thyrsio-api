package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.referentiel.dto.ArticleResponse;
import com.pierr.thyrsio_api.referentiel.dto.CreateArticleRequest;
import com.pierr.thyrsio_api.referentiel.dto.UpdateArticleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Référentiel — Articles")
@RestController
@RequestMapping("/api/referentiel/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(summary = "Lister les articles (paginé, filtrable)")
    @GetMapping
    public Page<ArticleResponse> list(
            @Parameter(description = "Code de catégorie, ex. BOUCHON") @RequestParam(required = false) String categoryCode,
            @Parameter(description = "Id du fournisseur principal") @RequestParam(required = false) UUID supplierId,
            @Parameter(description = "Texte recherché dans le code ou le libellé") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject Pageable pageable
    ) {
        return articleService.list(categoryCode, supplierId, search, includeArchived, pageable);
    }

    @Operation(summary = "Récupérer un article")
    @ApiResponse(responseCode = "200", description = "Article trouvé")
    @ApiResponse(responseCode = "404", description = "Article introuvable")
    @GetMapping("/{id}")
    public ArticleResponse get(@PathVariable UUID id) {
        return articleService.get(id);
    }

    @Operation(summary = "Créer un article", description = "Sans unité précisée, l'article prend celle de sa catégorie.")
    @ApiResponse(responseCode = "201", description = "Article créé")
    @ApiResponse(responseCode = "400", description = "Données invalides, catégorie, unité ou fournisseur inconnu ou archivé")
    @ApiResponse(responseCode = "409", description = "Code déjà utilisé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleResponse create(@Valid @RequestBody CreateArticleRequest request) {
        return articleService.create(request);
    }

    @Operation(summary = "Modifier un article", description = "Remplace la fiche entière. Le code et l'unité sont définitifs.")
    @ApiResponse(responseCode = "200", description = "Article modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides, catégorie ou fournisseur inconnu ou archivé")
    @ApiResponse(responseCode = "404", description = "Article introuvable")
    @PutMapping("/{id}")
    public ArticleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateArticleRequest request) {
        return articleService.update(id, request);
    }

    @Operation(summary = "Archiver un article")
    @ApiResponse(responseCode = "204", description = "Article archivé")
    @ApiResponse(responseCode = "404", description = "Article introuvable")
    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        articleService.archive(id);
    }

    @Operation(summary = "Réactiver un article")
    @ApiResponse(responseCode = "204", description = "Article réactivé")
    @ApiResponse(responseCode = "404", description = "Article introuvable")
    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID id) {
        articleService.reactivate(id);
    }
}

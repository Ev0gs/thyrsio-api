package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.referentiel.dto.ArticleCategoryResponse;
import com.pierr.thyrsio_api.referentiel.dto.CreateArticleCategoryRequest;
import com.pierr.thyrsio_api.referentiel.dto.UpdateArticleCategoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Référentiel — Catégories d'articles")
@RestController
@RequestMapping("/api/referentiel/categories")
public class ArticleCategoryController {

    private final ArticleCategoryService articleCategoryService;

    public ArticleCategoryController(ArticleCategoryService articleCategoryService) {
        this.articleCategoryService = articleCategoryService;
    }

    @Operation(summary = "Lister les catégories", description = "Les catégories archivées sont exclues, sauf avec includeArchived=true.")
    @GetMapping
    public List<ArticleCategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return articleCategoryService.list(includeArchived);
    }

    @Operation(summary = "Récupérer une catégorie")
    @ApiResponse(responseCode = "200", description = "Catégorie trouvée")
    @ApiResponse(responseCode = "404", description = "Catégorie introuvable")
    @GetMapping("/{id}")
    public ArticleCategoryResponse get(@PathVariable UUID id) {
        return articleCategoryService.get(id);
    }

    @Operation(summary = "Créer une catégorie")
    @ApiResponse(responseCode = "201", description = "Catégorie créée")
    @ApiResponse(responseCode = "400", description = "Données invalides ou unité inconnue")
    @ApiResponse(responseCode = "409", description = "Code déjà utilisé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleCategoryResponse create(@Valid @RequestBody CreateArticleCategoryRequest request) {
        return articleCategoryService.create(request);
    }

    @Operation(summary = "Modifier une catégorie")
    @ApiResponse(responseCode = "200", description = "Catégorie modifiée")
    @ApiResponse(responseCode = "400", description = "Données invalides ou unité inconnue")
    @ApiResponse(responseCode = "404", description = "Catégorie introuvable")
    @PutMapping("/{id}")
    public ArticleCategoryResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateArticleCategoryRequest request) {
        return articleCategoryService.update(id, request);
    }

    @Operation(summary = "Archiver une catégorie")
    @ApiResponse(responseCode = "204", description = "Catégorie archivée")
    @ApiResponse(responseCode = "404", description = "Catégorie introuvable")
    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        articleCategoryService.archive(id);
    }

    @Operation(summary = "Réactiver une catégorie")
    @ApiResponse(responseCode = "204", description = "Catégorie réactivée")
    @ApiResponse(responseCode = "404", description = "Catégorie introuvable")
    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID id) {
        articleCategoryService.reactivate(id);
    }

}

package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.common.DuplicateCodeException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.dto.ArticleCategoryResponse;
import com.pierr.thyrsio_api.referentiel.dto.CreateArticleCategoryRequest;
import com.pierr.thyrsio_api.referentiel.dto.UpdateArticleCategoryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ArticleCategoryService {

    private final ArticleCategoryRepository articleCategoryRepository;
    private final UnitRepository unitRepository;

    public ArticleCategoryService(ArticleCategoryRepository articleCategoryRepository, UnitRepository unitRepository) {
        this.articleCategoryRepository = articleCategoryRepository;
        this.unitRepository = unitRepository;
    }

    @Transactional(readOnly = true)
    public List<ArticleCategoryResponse> list(boolean includeArchived) {
        List<ArticleCategory> categories = includeArchived
                ? articleCategoryRepository.findAllByOrderByCodeAsc()
                : articleCategoryRepository.findByActiveTrueOrderByCodeAsc();
        return categories.stream().map(ArticleCategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ArticleCategoryResponse get(UUID id) {
        return ArticleCategoryResponse.from(findCategory(id));
    }

    @Transactional
    public ArticleCategoryResponse create(CreateArticleCategoryRequest request) {
        String code = UnitService.normalizeCode(request.code());
        if(articleCategoryRepository.existsByCode(code))
            throw new DuplicateCodeException("Catégorie", code);
        ArticleCategory articleCategory = new ArticleCategory(
                code,
                request.label().trim(),
                findUnitByCode(request.defaultUnitCode()),
                request.lotTracked(),
                request.regulated()
        );
        return ArticleCategoryResponse.from(articleCategoryRepository.save(articleCategory));
    }

    @Transactional
    public ArticleCategoryResponse update(UUID id, UpdateArticleCategoryRequest request) {
        ArticleCategory articleCategory = findCategory(id);
        articleCategory.setLabel(request.label().trim());
        articleCategory.setDefaultUnit(findUnitByCode(request.defaultUnitCode()));
        articleCategory.setLotTracked(request.lotTracked());
        articleCategory.setRegulated(request.regulated());
        return ArticleCategoryResponse.from(articleCategory);
    }

    @Transactional
    public void archive(UUID id) {
        findCategory(id).archive();
    }

    @Transactional
    public void reactivate(UUID id) {
        findCategory(id).reactivate();
    }

    // --- Méthodes utilitaires ---

    private ArticleCategory findCategory(UUID id) {
        return articleCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
    }

    /**
     * Une unité inconnue dans la requête est une erreur du client (400),
     * pas une ressource introuvable dans l'URL (404).
     */
    private Unit findUnitByCode(String code) {
        String normalized = UnitService.normalizeCode(code);
        return unitRepository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Unité inconnue : " + normalized));
    }
}

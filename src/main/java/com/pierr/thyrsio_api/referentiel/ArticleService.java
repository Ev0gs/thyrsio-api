package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.common.DuplicateCodeException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.dto.ArticleResponse;
import com.pierr.thyrsio_api.referentiel.dto.CreateArticleRequest;
import com.pierr.thyrsio_api.referentiel.dto.UpdateArticleRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleCategoryRepository articleCategoryRepository;
    private final UnitRepository unitRepository;
    private final SupplierRepository supplierRepository;

    public ArticleService(ArticleRepository articleRepository, ArticleCategoryRepository articleCategoryRepository, UnitRepository unitRepository, SupplierRepository supplierRepository) {
        this.articleRepository = articleRepository;
        this.articleCategoryRepository = articleCategoryRepository;
        this.unitRepository = unitRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public Page<ArticleResponse> list(String categoryCode,
                                      UUID supplierId,
                                      String search,
                                      boolean includeArchived,
                                      Pageable pageable) {
        // On ne garde que les filtres demandés
        List<Specification<Article>> filters = new ArrayList<>();
        if(!includeArchived) {
            filters.add(ArticleSpecifications.isActive());
        }
        if(categoryCode != null && !categoryCode.isBlank()) {
            filters.add(ArticleSpecifications.inCategory(categoryCode));
        }
        if(supplierId != null) {
            filters.add(ArticleSpecifications.fromSupplier(supplierId));
        }
        if(search != null && !search.isBlank()) {
            filters.add(ArticleSpecifications.matches(search));
        }
        // allOf : combine les filtres avec des AND (aucun filtre = tous les articles)
        return articleRepository.findAll(Specification.allOf(filters), pageable)
                .map(ArticleResponse::from);
    }

    @Transactional(readOnly = true)
    public ArticleResponse get(UUID id) {
        return ArticleResponse.from(findArticle(id));
    }

    @Transactional
    public ArticleResponse create(CreateArticleRequest request) {
        String code = UnitService.normalizeCode(request.code());
        if(articleRepository.existsByCode(code)) {
            throw new DuplicateCodeException("Article", code);
        }

        ArticleCategory category = findActiveCategory(request.categoryCode());
        // Unité demandée, sinon celle de la catégorie
        Unit unit = (request.unitCode() == null || request.unitCode().isBlank())
                ? category.getDefaultUnit()
                : findUnit(request.unitCode());

        Article article = new Article(
                code,
                request.label().trim(),
                category,
                unit
        );
        article.setMainSupplier(findActiveSupplierOrNull(request.mainSupplierId()));
        article.setSupplierReference(blankToNull(request.supplierReference()));
        article.setAlertThreshold(checkDecimals(request.alertThreshold(), unit));

        return ArticleResponse.from(articleRepository.save(article));
    }

    @Transactional
    public ArticleResponse update(UUID id, UpdateArticleRequest request) {
        Article article = findArticle(id);
        article.setLabel(request.label().trim());
        article.setCategory(findActiveCategory(request.categoryCode()));
        article.setMainSupplier(findActiveSupplierOrNull(request.mainSupplierId()));
        article.setSupplierReference(blankToNull(request.supplierReference()));
        article.setAlertThreshold(checkDecimals(request.alertThreshold(), article.getUnit()));
        return ArticleResponse.from(article);
    }

    @Transactional
    public void archive(UUID id) {
        findArticle(id).archive();
    }

    @Transactional
    public void reactivate(UUID id) {
        findArticle(id).reactivate();
    }

    // --- Méthodes utilitaires ---

    private Article findArticle(UUID id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
    }

    private ArticleCategory findActiveCategory(String code) {
        String normalized = UnitService.normalizeCode(code);
        ArticleCategory category = articleCategoryRepository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie inconnue : " + normalized));
        if(!category.isActive()) {
            throw new IllegalArgumentException("Catégorie archivée : " + normalized);
        }
        return category;
    }

    private Unit findUnit(String code) {
        String normalized = UnitService.normalizeCode(code);
        return unitRepository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Unité inconnue : " + normalized));
    }

    private Supplier findActiveSupplierOrNull(UUID supplierId) {
        if(supplierId == null) {
            return null;
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Fournisseur inconnu : " + supplierId));
        if(!supplier.isActive()) {
            throw new IllegalArgumentException("Fournisseur archivé : " + supplier.getName());
        }
        return supplier;
    }

    /**
     * Une quantité ne peut pas avoir plus de décimales que son unité :
     * 2,5 bouteilles est refusé, 2,5 litres est accepté.
     */
    private static BigDecimal checkDecimals(BigDecimal quantity, Unit unit) {
        if(quantity == null) {
            return null;
        }
        // stripTrailingZeros : 12,500 n'a en réalité qu'une décimale
        if(quantity.stripTrailingZeros().scale() > unit.getDecimals()) {
            throw new IllegalArgumentException("Trop de décimales pour l'unité " + unit.getCode() + " : " +unit.getDecimals() + " au maximum");
        }
        return quantity;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

/**
 * Les filtres de la liste des articles. Chacun est une condition WHERE indépendante ;
 * le service ne garde que ceux que le client a demandés, puis les combine.
 */
final class ArticleSpecifications {

    private ArticleSpecifications() {
    }

    static Specification<Article> isActive() {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.isTrue(root.get("active"));
    }

    static Specification<Article> inCategory(String categoryCode) {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.equal(root.get("category").get("code"), categoryCode);
    }

    static Specification<Article> fromSupplier(UUID supplierId) {
        return (root, query, criteriaBuilder)
                -> criteriaBuilder.equal(root.get("mainSupplier").get("id"), supplierId);
    }

    /** Recherche dans le code ou le libellé, sans tenir compte des majuscules. */
    static Specification<Article> matches(String text) {
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("label")), pattern)
        );
    }
}

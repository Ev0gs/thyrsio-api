package com.pierr.thyrsio_api.referentiel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ce que les autres modules ont le droit de savoir d'un article.
 * Lecture seule : aucun autre module ne manipule l'entité Article.
 */
public record ArticleInfo(
        UUID id,
        String code,
        String label,
        String categoryCode,
        String categoryLabel,
        String unitCode,
        int unitDecimals,
        boolean lotTracked,
        BigDecimal alertThreshold,  // null : pas de seuil
        boolean active
) {
}
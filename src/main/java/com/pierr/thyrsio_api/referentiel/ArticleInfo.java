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
    /** La catégorie des bouteilles produites par les mises (créée par la migration V14). */
    public static final String FINISHED_GOODS_CATEGORY = "PRODUIT_FINI";

    /** Un produit fini (une bouteille de vin), par opposition à une matière sèche. */
    public boolean finishedGood() {
        return FINISHED_GOODS_CATEGORY.equals(categoryCode);
    }
}

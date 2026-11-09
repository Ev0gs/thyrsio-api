package com.pierr.thyrsio_api.referentiel;

import java.util.Collection;
import java.util.UUID;

/**
 * Question posée par le référentiel, à laquelle le module Stock répond.
 * Le référentiel définit l'interface, le stock l'implémente : ainsi le référentiel
 * ne dépend pas du stock (pas de dépendance circulaire).
 */
public interface ArticleStockUsage {

    /** Vrai si au moins un de ces articles a déjà des lots ou des mouvements de stock. */
    boolean isUsedInStock(Collection<UUID> articleIds);
}
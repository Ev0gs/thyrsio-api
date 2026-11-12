package com.pierr.thyrsio_api.referentiel;

import java.util.UUID;

/**
 * Ce que les autres modules ont le droit de savoir d'un fournisseur.
 * Lecture seule, comme ArticleInfo : aucun autre module ne manipule l'entité Supplier.
 */
public record SupplierInfo(
        UUID id,
        String name,
        SupplierKind kind,
        boolean active
) {
}

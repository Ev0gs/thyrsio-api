package com.pierr.thyrsio_api.sales;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Ce que les mises prévoient pour les commandes. Une interface, comme ArticleStockUsage :
 * le module Ventes la définit, le module Production l'implémente. Ventes ne dépend donc pas de Production
 * (qui, lui, dépend de Ventes pour vérifier les lignes de commande) : pas de dépendance circulaire.
 */
public interface PlannedSupply {

    /** Une part d'une mise réservée à une ligne de commande. */
    record Allocation(UUID orderLineId, UUID bottlingId, String bottlingCode, LocalDate startDate,
                      String bottlingStatus, int quantity) {
        public boolean planned() {
            return "PLANNED".equals(bottlingStatus);
        }

        public boolean closed() {
            return "CLOSED".equals(bottlingStatus);
        }
    }

    /** Les allocations de ces lignes, mises annulées exclues. */
    List<Allocation> allocationsFor(Collection<UUID> orderLineIds);
}

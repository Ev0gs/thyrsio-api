package com.pierr.thyrsio_api.sales;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Les expéditions d'une commande, vues depuis le module Ventes.
 * Définie ici, implémentée par le module Expéditions (même principe que PlannedSupply).
 */
public interface OrderShipments {

    record Summary(UUID id, String code, String status, LocalDate plannedDate, Instant shippedAt, int bottles) {
    }

    List<Summary> forOrder(UUID orderId);

    /** Une expédition en préparation empêche d'annuler ou de modifier la commande. */
    boolean hasDraft(UUID orderId);
}

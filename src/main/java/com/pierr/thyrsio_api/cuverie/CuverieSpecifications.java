package com.pierr.thyrsio_api.cuverie;

import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

final class CuverieSpecifications {

    private CuverieSpecifications() {
    }

    // --- Lots ---

    /** Les lots encore présents dans au moins une cuve. */
    static Specification<WineLot> lotInTank() {
        return (root, query, cb) -> {
            Subquery<UUID> tanks = query.subquery(UUID.class);
            var tank = tanks.from(Tank.class);
            tanks.select(tank.get("id")).where(cb.equal(tank.get("wineLot"), root));
            return cb.exists(tanks);
        };
    }

    /** Recherche dans le code, l'appellation et le n° de lot du vendeur, sans tenir compte de la casse. */
    static Specification<WineLot> lotMatches(String search) {
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("appellation")), pattern),
                cb.like(cb.lower(root.get("supplierLotNumber")), pattern));
    }

    static Specification<WineLot> lotOfSupplier(UUID supplierId) {
        return (root, query, cb) -> cb.equal(root.get("supplierId"), supplierId);
    }

    // --- Mouvements ---

    static Specification<TankMovement> movementOfTank(UUID tankId) {
        return (root, query, cb) -> cb.equal(root.get("tank").get("id"), tankId);
    }

    static Specification<TankMovement> movementOfLot(UUID wineLotId) {
        return (root, query, cb) -> cb.equal(root.get("wineLot").get("id"), wineLotId);
    }

    static Specification<TankMovement> movementOfType(TankMovementType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    static Specification<TankMovement> movementOfTransfer(UUID transferId) {
        return (root, query, cb) -> cb.equal(root.get("transferId"), transferId);
    }

    /** Inclus : à partir de cet instant. */
    static Specification<TankMovement> occurredFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from);
    }

    /** Exclu : strictement avant cet instant. */
    static Specification<TankMovement> occurredBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.<Instant>get("occurredAt"), to);
    }
}

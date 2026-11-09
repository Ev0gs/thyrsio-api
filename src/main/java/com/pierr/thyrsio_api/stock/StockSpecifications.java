package com.pierr.thyrsio_api.stock;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class StockSpecifications {

    private StockSpecifications() {
    }

    // --- Mouvements ---

    static Specification<StockMovement> movementOfArticle(UUID articleId) {
        return (root, query, cb) -> cb.equal(root.get("articleId"), articleId);
    }

    static Specification<StockMovement> movementOfLot(UUID lotId) {
        return (root, query, cb) -> cb.equal(root.get("lot").get("id"), lotId);
    }

    static Specification<StockMovement> movementOfType(MovementType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    /** Inclus : à partir de cet instant. */
    static Specification<StockMovement> occurredFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from);
    }

    /** Exclu : strictement avant cet instant. */
    static Specification<StockMovement> occurredBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.<Instant>get("occurredAt"), to);
    }

    // --- Soldes ---

    static Specification<StockBalance> balanceOfArticle(UUID articleId) {
        return (root, query, cb) -> cb.equal(root.get("articleId"), articleId);
    }

    static Specification<StockBalance> balanceNotEmpty() {
        return (root, query, cb) -> cb.greaterThan(root.<BigDecimal>get("quantity"), BigDecimal.ZERO);
    }
}
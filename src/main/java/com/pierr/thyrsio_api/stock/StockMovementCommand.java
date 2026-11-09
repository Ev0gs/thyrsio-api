package com.pierr.thyrsio_api.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Une demande de mouvement, quelle que soit son origine (API ou autre module).
 * quantity : positive pour IN/OUT, signée pour EITHER (voir MovementType).
 * lotId, occurredAt (défaut : maintenant), reference et notes sont facultatifs.
 */
public record StockMovementCommand(
        UUID articleId,
        UUID lotId,
        MovementType type,
        BigDecimal quantity,
        Instant occurredAt,
        String reference,
        String notes,
        UUID createdBy
) {
}
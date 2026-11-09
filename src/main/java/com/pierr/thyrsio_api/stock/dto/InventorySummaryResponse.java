package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.stock.Inventory;
import com.pierr.thyrsio_api.stock.InventoryStatus;

import java.time.Instant;
import java.util.UUID;

/** Pour la liste : sans les lignes (sinon une requête de plus par inventaire). */
public record InventorySummaryResponse(
        UUID id,
        String label,
        InventoryStatus status,
        Instant countedAt,
        Instant createdAt,
        Instant validatedAt
) {
    public static InventorySummaryResponse from(Inventory inventory) {
        return new InventorySummaryResponse(
                inventory.getId(),
                inventory.getLabel(),
                inventory.getStatus(),
                inventory.getCountedAt(),
                inventory.getCreatedAt(),
                inventory.getValidatedAt()
        );
    }
}
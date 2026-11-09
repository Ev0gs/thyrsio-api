package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.stock.Inventory;
import com.pierr.thyrsio_api.stock.InventoryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryResponse(
        UUID id,
        String label,
        InventoryStatus status,
        Instant countedAt,
        String notes,
        UUID createdBy,
        Instant createdAt,
        UUID validatedBy,
        Instant validatedAt,
        List<InventoryLineResponse> lines
) {
    public static InventoryResponse from(Inventory inventory, List<InventoryLineResponse> lines) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getLabel(),
                inventory.getStatus(),
                inventory.getCountedAt(),
                inventory.getNotes(),
                inventory.getCreatedBy(),
                inventory.getCreatedAt(),
                inventory.getValidatedBy(),
                inventory.getValidatedAt(),
                lines
        );
    }
}
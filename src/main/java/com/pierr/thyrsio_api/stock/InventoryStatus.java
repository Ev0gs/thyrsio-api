package com.pierr.thyrsio_api.stock;

public enum InventoryStatus {
    /** En cours de comptage : modifiable */
    DRAFT,
    /** Écarts appliqués au stock : définitif */
    VALIDATED,
    /** Abandonné sans effet sur le stock */
    CANCELLED
}
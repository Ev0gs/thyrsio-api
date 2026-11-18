package com.pierr.thyrsio_api.production.dto;

import com.pierr.thyrsio_api.production.ProductionSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Une production de la mise : un produit fini, quand, combien, d'où vient le vin, et ce qui fait son numéro
 * de lot (l'appellation et le millésime). endDate : null pour une production sur un seul jour.
 */
public record ProductionRequest(
        UUID id,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        ProductionSlot slot,
        @NotNull UUID finishedArticleId,
        @NotNull @Positive @Max(10_000_000) Integer plannedBottles,
        @NotNull @Positive @Max(10_000) Integer bottleVolumeCl,
        UUID sourceTankId,
        @Size(max = 200) String wineDescription,
        @NotNull UUID appellationId,
        @Min(1900) @Max(2100) Integer vintage,
        // null ou vide : pas encore de matières prévues
        @Size(max = 50) List<@Valid @NotNull MaterialRequest> materials,
        // Les commandes que cette production sert : null ou vide, elle produit pour le stock
        @Size(max = 50) List<@Valid @NotNull AllocationRequest> allocations
) {
}

package com.pierr.thyrsio_api.production.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Des bouteilles de la mise réservées à une ligne de commande. */
public record AllocationRequest(
        @NotNull UUID orderLineId,
        @NotNull @Positive @Max(10_000_000) Integer quantity
) {
}

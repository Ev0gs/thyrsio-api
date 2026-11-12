package com.pierr.thyrsio_api.shipping.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Une ligne chargée : quelle ligne de commande, quel lot de bouteilles, combien. */
public record ShipmentLineRequest(
        @NotNull UUID orderLineId,
        @NotNull UUID lotId,
        @NotNull @Positive @Max(10_000_000) Integer quantity
) {
}

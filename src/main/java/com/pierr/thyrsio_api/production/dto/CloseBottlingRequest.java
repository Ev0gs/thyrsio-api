package com.pierr.thyrsio_api.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Le réel de la mise, saisi à la clôture (ce qui était noté sur papier).
 * wineUsedHl : obligatoire pour une mise chez nous (le vin sort de la cuve), absent chez le producteur.
 * consumptions : un même article peut figurer plusieurs fois, une fois par lot consommé.
 */
public record CloseBottlingRequest(
        @NotNull @Positive @Max(10_000_000) Integer producedBottles,
        @PositiveOrZero @Max(10_000_000) Integer brokenBottles,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal wineUsedHl,
        @NotNull @Size(max = 100) List<@Valid @NotNull ConsumptionRequest> consumptions
) {
}

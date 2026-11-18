package com.pierr.thyrsio_api.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/** Le réel d'une production : les bouteilles produites, la casse, le vin tiré, les matières consommées. */
public record CloseProductionRequest(
        @NotNull @Positive @Max(10_000_000) Integer producedBottles,
        @PositiveOrZero @Max(10_000_000) Integer brokenBottles,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal wineUsedHl,
        @NotNull @Size(max = 100) List<@Valid @NotNull ConsumptionRequest> consumptions
) {
}

package com.pierr.thyrsio_api.cuverie.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;
import java.math.BigDecimal;

/** Une cuve remplie lors d'une réception, et le volume qu'on y a pompé. */
public record TankFill(
        @NotNull UUID tankId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal volumeHl
) {
}

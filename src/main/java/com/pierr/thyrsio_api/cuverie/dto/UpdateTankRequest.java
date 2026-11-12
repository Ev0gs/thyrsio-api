package com.pierr.thyrsio_api.cuverie.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Le code ne change pas : il est peint sur la cuve et cité dans l'historique. */
public record UpdateTankRequest(
        @Size(max = 100) String label,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal capacityHl
) {
}

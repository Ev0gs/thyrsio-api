package com.pierr.thyrsio_api.production.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Une matière sèche prévue pour la mise. */
public record MaterialRequest(
        @NotNull UUID articleId,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal plannedQuantity
) {
}

package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Modification complète (PUT). Le code et l'unité sont définitifs. */
public record UpdateArticleRequest(
        @NotBlank @Size(max = 200) String label,
        @NotBlank String categoryCode,
        UUID mainSupplierId,
        @Size(max = 100) String supplierReference,
        @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal alertThreshold
) {
}

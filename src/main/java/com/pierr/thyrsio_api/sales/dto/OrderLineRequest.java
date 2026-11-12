package com.pierr.thyrsio_api.sales.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Un produit fini commandé, en bouteilles, avec son prix unitaire (facultatif). */
public record OrderLineRequest(
        @NotNull UUID articleId,
        @NotNull @Positive @Max(10_000_000) Integer quantity,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitPrice
) {
}

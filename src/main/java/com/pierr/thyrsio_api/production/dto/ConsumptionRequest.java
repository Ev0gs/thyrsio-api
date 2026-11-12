package com.pierr.thyrsio_api.production.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Ce qui a réellement été consommé : un article, son lot s'il est suivi par lot, une quantité. */
public record ConsumptionRequest(
        @NotNull UUID articleId,
        UUID lotId,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal quantity
) {
}

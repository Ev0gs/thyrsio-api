package com.pierr.thyrsio_api.stock.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record AddInventoryLineRequest(
        @NotNull UUID articleId,
        UUID lotId,
        // Facultatif : on peut ajouter la ligne et la compter plus tard
        @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal countedQuantity,
        @Size(max = 500) String notes
) {
}
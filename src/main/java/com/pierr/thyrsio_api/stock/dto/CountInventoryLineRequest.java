package com.pierr.thyrsio_api.stock.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CountInventoryLineRequest(
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal countedQuantity,
        @Size(max = 500) String notes
) {
}
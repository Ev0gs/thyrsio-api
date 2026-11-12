package com.pierr.thyrsio_api.cuverie.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Toutes les valeurs sont facultatives, mais au moins une valeur ou une note est demandée. */
public record AnalysisRequest(
        // Facultatif : la cuve prélevée
        UUID tankId,
        @NotNull @PastOrPresent LocalDate analyzedOn,
        @DecimalMin("0") @DecimalMax("25") @Digits(integer = 2, fraction = 2) BigDecimal alcohol,
        @PositiveOrZero @Digits(integer = 5, fraction = 1) BigDecimal freeSo2,
        @PositiveOrZero @Digits(integer = 5, fraction = 1) BigDecimal totalSo2,
        @PositiveOrZero @Digits(integer = 3, fraction = 2) BigDecimal totalAcidity,
        @PositiveOrZero @Digits(integer = 3, fraction = 2) BigDecimal volatileAcidity,
        @Size(max = 200) String laboratory,
        @Size(max = 2000) String notes
) {
}

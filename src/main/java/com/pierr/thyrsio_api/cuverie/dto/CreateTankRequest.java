package com.pierr.thyrsio_api.cuverie.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateTankRequest(
        // Mis en majuscules par le service : "c01" devient "C01"
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
        @Size(max = 100) String label,
        // Hectolitres, 2 décimales (au litre près), comme la colonne numeric(10, 2)
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal capacityHl
) {
}

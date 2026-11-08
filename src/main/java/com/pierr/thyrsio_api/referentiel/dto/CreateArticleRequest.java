package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateArticleRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
        @NotBlank @Size(max = 200) String label,
        @NotBlank String categoryCode,
        // Facultatif : par défaut, l'unité de la catégorie
        String unitCode,
        UUID mainSupplierId,
        @Size(max = 100) String supplierReference,
        // @Digits : au plus 10 chiffres avant la virgule et 4 après, comme la colonne numeric(14, 4)
        @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal alertThreshold
) {
}

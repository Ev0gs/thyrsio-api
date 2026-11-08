package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateArticleCategoryRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String label,
        // Le code de l'unité (ex. "L"), plus simple pour le client qu'un UUID
        @NotBlank String defaultUnitCode,
        @NotNull Boolean lotTracked,
        @NotNull Boolean regulated
) {
}

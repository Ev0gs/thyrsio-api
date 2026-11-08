package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateArticleCategoryRequest(
        @NotBlank @Size(max = 100) String label,
        @NotBlank String defaultUnitCode,
        @NotNull Boolean lotTracked,
        @NotNull Boolean regulated
) {
}

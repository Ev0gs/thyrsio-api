package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.*;

public record CreateUnitRequest(
        @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Za-z0-9_]+$") String code,
        @NotBlank @Size(max = 254) String label,
        @NotNull @Min(0) @Max(6) short decimals
) {
}

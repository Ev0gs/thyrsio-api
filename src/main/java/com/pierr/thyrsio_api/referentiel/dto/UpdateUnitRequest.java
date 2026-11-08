package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUnitRequest(
        @NotBlank @Size(max = 254) String label
) {
}

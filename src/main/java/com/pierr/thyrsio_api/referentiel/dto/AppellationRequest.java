package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST et PUT /api/referentiel/appellations. Le sigle entre dans les numéros de lot : BX donne LBX2401. */
public record AppellationRequest(
        @NotBlank @Pattern(regexp = "^\\s*[A-Za-z]{1,4}\\s*$", message = "1 à 4 lettres, sans chiffre ni espace")
        String code,
        @NotBlank @Size(max = 100) String name
) {
}

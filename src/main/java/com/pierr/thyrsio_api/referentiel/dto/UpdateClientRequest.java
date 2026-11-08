package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.Incoterm;
import jakarta.validation.constraints.*;

/** Remplacement complet (PUT) : tout sauf le code, qui est définitif. */
public record UpdateClientRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "code pays ISO à 2 lettres attendu (FR, BE, JP...)")
        String countryCode,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "code langue ISO à 2 lettres attendu (fr, en, ja...)")
        String language,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String city,
        @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @Size(max = 20) String vatNumber,
        @Size(max = 20) String exciseNumber,
        Incoterm defaultIncoterm,
        boolean gs1LabelingRequired,
        @Size(max = 2000) String notes
) implements ClientFields {
}
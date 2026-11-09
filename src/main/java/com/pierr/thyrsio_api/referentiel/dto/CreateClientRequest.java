package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.Incoterm;
import jakarta.validation.constraints.*;

public record CreateClientRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
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
        Boolean gs1LabelingRequired,
        @Size(max = 2000) String notes
) implements ClientFields {
}
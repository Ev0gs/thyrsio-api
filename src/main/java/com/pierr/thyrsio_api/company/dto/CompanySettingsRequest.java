package com.pierr.thyrsio_api.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** PUT /api/company : remplacement complet (un champ vide efface la valeur). */
public record CompanySettingsRequest(
        @Size(max = 200) String legalName,
        @Size(max = 20) String exciseNumber,
        @Size(max = 20) String siret,
        @Size(max = 20) String vatNumber,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String city,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "code pays ISO à 2 lettres attendu (FR, BE...)") String countryCode,
        @Size(max = 30) String phone,
        @Email @Size(max = 254) String email,
        LocalDate registerStartDate
) {
}

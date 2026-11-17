package com.pierr.thyrsio_api.company.dto;

import com.pierr.thyrsio_api.company.CompanySettings;

import java.time.Instant;
import java.time.LocalDate;

public record CompanySettingsResponse(
        String legalName,
        String exciseNumber,
        String siret,
        String vatNumber,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        String phone,
        String email,
        LocalDate registerStartDate,
        Instant updatedAt
) {
    public static CompanySettingsResponse from(CompanySettings s) {
        return new CompanySettingsResponse(s.getLegalName(), s.getExciseNumber(), s.getSiret(), s.getVatNumber(),
                s.getAddressLine1(), s.getAddressLine2(), s.getPostalCode(), s.getCity(), s.getCountryCode(),
                s.getPhone(), s.getEmail(), s.getRegisterStartDate(), s.getUpdatedAt());
    }
}

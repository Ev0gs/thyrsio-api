package com.pierr.thyrsio_api.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeliveryAddressRequest(
        @NotBlank @Size(max = 100) String label,
        @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "code pays ISO à 2 lettres attendu (FR, BE, JP...)")
        String countryCode,
        @Size(max = 30) String phone,
        @Size(max = 1000) String deliveryInstructions,
        Boolean defaultAddress   // absent = false
) {
        /** Vrai seulement si defaultAddress vaut explicitement true. */
        public boolean wantsDefault() {
                return Boolean.TRUE.equals(defaultAddress);
        }
}
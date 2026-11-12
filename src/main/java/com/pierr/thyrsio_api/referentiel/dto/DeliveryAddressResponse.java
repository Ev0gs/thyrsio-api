package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.GeoLocation;
import com.pierr.thyrsio_api.referentiel.ClientDeliveryAddress;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAddressResponse(
        UUID id,
        UUID clientId,
        String label,
        String recipientName,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        String phone,
        String deliveryInstructions,
        boolean defaultAddress,
        boolean active,
        Instant createdAt,
        GeoLocation location
) {
    public static DeliveryAddressResponse from(ClientDeliveryAddress address) {
        return new DeliveryAddressResponse(
                address.getId(),
                address.getClient().getId(), // getId() sur un proxy LAZY ne déclenche pas de requête
                address.getLabel(),
                address.getRecipientName(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getPostalCode(),
                address.getCity(),
                address.getCountryCode(),
                address.getPhone(),
                address.getDeliveryInstructions(),
                address.isDefaultAddress(),
                address.isActive(),
                address.getCreatedAt(),
                address.getLocation()
        );
    }
}
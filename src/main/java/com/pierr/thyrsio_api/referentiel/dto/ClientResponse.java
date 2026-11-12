package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.GeoLocation;
import com.pierr.thyrsio_api.referentiel.LocationStatus;
import com.pierr.thyrsio_api.referentiel.Client;
import com.pierr.thyrsio_api.referentiel.Incoterm;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String code,
        String name,
        String countryCode,
        String language,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String email,
        String phone,
        String vatNumber,
        String exciseNumber,
        Incoterm defaultIncoterm,
        boolean gs1LabelingRequired,
        String notes,
        boolean active,
        Instant createdAt,
        GeoLocation location,
        LocationStatus locationStatus
) {
    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getCode(),
                client.getName(),
                client.getCountryCode(),
                client.getLanguage(),
                client.getAddressLine1(),
                client.getAddressLine2(),
                client.getPostalCode(),
                client.getCity(),
                client.getEmail(),
                client.getPhone(),
                client.getVatNumber(),
                client.getExciseNumber(),
                client.getDefaultIncoterm(),
                client.isGs1LabelingRequired(),
                client.getNotes(),
                client.isActive(),
                client.getCreatedAt(),
                client.getLocation(),
                client.getLocationStatus()
        );
    }
}
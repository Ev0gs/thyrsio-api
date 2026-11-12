package com.pierr.thyrsio_api.referentiel;

import java.util.UUID;

/** Ce que les autres modules ont le droit de savoir d'un client (lecture seule). */
public record ClientInfo(
        UUID id,
        String code,
        String name,
        String countryCode,
        String language,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String vatNumber,
        String exciseNumber,
        Incoterm defaultIncoterm,
        boolean active
) {
    static ClientInfo from(Client client) {
        return new ClientInfo(client.getId(), client.getCode(), client.getName(), client.getCountryCode(),
                client.getLanguage(), client.getAddressLine1(), client.getAddressLine2(), client.getPostalCode(),
                client.getCity(), client.getVatNumber(), client.getExciseNumber(), client.getDefaultIncoterm(),
                client.isActive());
    }
}

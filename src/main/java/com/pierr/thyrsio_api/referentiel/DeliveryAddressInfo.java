package com.pierr.thyrsio_api.referentiel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Une adresse de livraison, pour les autres modules (commandes, expéditions). */
public record DeliveryAddressInfo(
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
        boolean active,
        GeoLocation location
) {
    static DeliveryAddressInfo from(ClientDeliveryAddress address) {
        return new DeliveryAddressInfo(address.getId(), address.getClient().getId(), address.getLabel(),
                address.getRecipientName(), address.getAddressLine1(), address.getAddressLine2(),
                address.getPostalCode(), address.getCity(), address.getCountryCode(), address.getPhone(),
                address.getDeliveryInstructions(), address.isActive(), address.getLocation());
    }

    /** L'adresse prête à imprimer, une ligne par élément (destinataire par défaut : le nom du client). */
    public String toPostalText(String defaultRecipient) {
        List<String> lines = new ArrayList<>();
        lines.add(recipientName != null ? recipientName : defaultRecipient);
        lines.add(addressLine1);
        if (addressLine2 != null) lines.add(addressLine2);
        lines.add((postalCode != null ? postalCode + " " : "") + city);
        lines.add(countryCode);
        if (phone != null) lines.add("Tél. " + phone);
        if (deliveryInstructions != null) lines.add(deliveryInstructions);
        return String.join("\n", lines);
    }
}

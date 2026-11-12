package com.pierr.thyrsio_api.shipping.dto;

import com.pierr.thyrsio_api.referentiel.GeoLocation;
import com.pierr.thyrsio_api.referentiel.Incoterm;
import com.pierr.thyrsio_api.shipping.ShipmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La fiche d'une expédition, et tout ce qu'il faut pour imprimer son bon de livraison.
 * deliveryAddressText : figée au départ ; avant, l'adresse actuelle de la commande.
 * deliveryLocation : la position de l'adresse de livraison (ou du siège) sur la carte, si elle est connue.
 */
public record ShipmentResponse(
        UUID id,
        String code,
        ShipmentStatus status,
        UUID orderId,
        String orderCode,
        String customerReference,
        Incoterm incoterm,
        UUID clientId,
        String clientCode,
        String clientName,
        String clientAddress,
        String clientVatNumber,
        String clientExciseNumber,
        String clientLanguage,
        String deliveryAddressText,
        GeoLocation deliveryLocation,
        LocalDate plannedDate,
        String carrier,
        String transportReference,
        String notes,
        List<Line> lines,
        int totalBottles,
        Instant shippedAt,
        UUID shippedBy,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    /** orderedQuantity / remainingQuantity : de la ligne de commande, pour préparer le chargement. */
    public record Line(
            UUID id,
            UUID orderLineId,
            UUID articleId,
            String articleCode,
            String articleLabel,
            UUID lotId,
            String lotNumber,
            int quantity,
            int orderedQuantity,
            int remainingQuantity
    ) {
    }
}

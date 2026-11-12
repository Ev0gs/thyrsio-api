package com.pierr.thyrsio_api.sales.dto;

import com.pierr.thyrsio_api.referentiel.DeliveryAddressInfo;
import com.pierr.thyrsio_api.referentiel.Incoterm;
import com.pierr.thyrsio_api.sales.OrderShipments;
import com.pierr.thyrsio_api.sales.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** La fiche complète d'une commande : ses lignes et leur couverture, les mises prévues, les expéditions. */
public record OrderResponse(
        UUID id,
        String code,
        OrderStatus status,
        UUID clientId,
        String clientCode,
        String clientName,
        String clientCountryCode,
        UUID deliveryAddressId,
        DeliveryAddressInfo deliveryAddress,
        String customerReference,
        LocalDate orderDate,
        LocalDate requestedLoadingDate,
        Incoterm incoterm,
        String notes,
        List<Line> lines,
        int totalBottles,
        int shippedBottles,
        // null si aucune ligne n'a de prix
        BigDecimal totalAmount,
        List<OrderShipments.Summary> shipments,
        Instant confirmedAt,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Une ligne. fromStock / fromBottlings / missing : seulement pour une commande confirmée qui attend
     * encore des bouteilles (null sinon).
     */
    public record Line(
            UUID id,
            UUID articleId,
            String articleCode,
            String articleLabel,
            int quantity,
            BigDecimal unitPrice,
            int shippedQuantity,
            int remaining,
            Integer fromStock,
            Integer fromBottlings,
            Integer missing,
            List<Allocation> allocations
    ) {
    }

    /** Une mise qui produit pour cette ligne. */
    public record Allocation(UUID bottlingId, String bottlingCode, LocalDate startDate, String bottlingStatus,
                             int quantity) {
    }
}

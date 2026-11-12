package com.pierr.thyrsio_api.sales.dto;

import com.pierr.thyrsio_api.sales.OrderStatus;

import java.time.LocalDate;
import java.util.UUID;

/** Une commande dans la liste. missingBottles : ce qui n'est couvert ni par le stock ni par une mise. */
public record OrderRow(
        UUID id,
        String code,
        OrderStatus status,
        UUID clientId,
        String clientName,
        String clientCountryCode,
        String customerReference,
        LocalDate orderDate,
        LocalDate requestedLoadingDate,
        int lineCount,
        int totalBottles,
        int shippedBottles,
        int missingBottles
) {
}

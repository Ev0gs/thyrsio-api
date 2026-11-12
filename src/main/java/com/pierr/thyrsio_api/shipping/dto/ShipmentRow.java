package com.pierr.thyrsio_api.shipping.dto;

import com.pierr.thyrsio_api.shipping.ShipmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShipmentRow(
        UUID id,
        String code,
        ShipmentStatus status,
        UUID orderId,
        String orderCode,
        String clientName,
        String clientCountryCode,
        LocalDate plannedDate,
        Instant shippedAt,
        String carrier,
        int totalBottles
) {
}

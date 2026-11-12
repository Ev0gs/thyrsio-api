package com.pierr.thyrsio_api.shipping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Préparer une expédition (POST, orderId obligatoire) ou la modifier tant qu'elle est en préparation
 * (PUT : orderId ignoré, les lignes sont remplacées).
 */
public record ShipmentRequest(
        UUID orderId,
        @NotNull LocalDate plannedDate,
        @Size(max = 200) String carrier,
        @Size(max = 100) String transportReference,
        @Size(max = 2000) String notes,
        @NotNull @Size(max = 200) List<@Valid @NotNull ShipmentLineRequest> lines
) {
}

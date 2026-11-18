package com.pierr.thyrsio_api.production.dto;

import com.pierr.thyrsio_api.production.BottlingLocation;
import com.pierr.thyrsio_api.production.BottlingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Une mise et ses productions, dans l'ordre où elles ont lieu.
 * plannedBottles : le total prévu ; shortageCount : les matières manquantes, toutes productions confondues ;
 * alert : une production planifiée manque de matières à moins de quelques jours.
 */
public record BottlingResponse(
        UUID id,
        String code,
        BottlingStatus status,
        BottlingLocation location,
        String contractor,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        int plannedBottles,
        int producedBottles,
        int shortageCount,
        boolean alert,
        List<ProductionResponse> productions,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}

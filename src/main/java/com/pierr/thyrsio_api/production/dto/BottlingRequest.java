package com.pierr.thyrsio_api.production.dto;

import com.pierr.thyrsio_api.production.BottlingLocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Planifier une mise (POST) ou modifier une mise planifiée (PUT : la liste des matières est remplacée).
 * ON_SITE : sourceTankId obligatoire. AT_PRODUCER : pas de cuve, wineDescription décrit le vin.
 */
public record BottlingRequest(
        @NotNull BottlingLocation location,
        @Size(max = 200) String contractor,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull UUID finishedArticleId,
        @NotNull @Positive @Max(10_000_000) Integer plannedBottles,
        @NotNull @Positive @Max(10_000) Integer bottleVolumeCl,
        UUID sourceTankId,
        @Size(max = 200) String wineDescription,
        @Size(max = 2000) String notes,
        // null ou vide : pas encore de matières prévues
        @Size(max = 50) List<@Valid @NotNull MaterialRequest> materials
) {
}

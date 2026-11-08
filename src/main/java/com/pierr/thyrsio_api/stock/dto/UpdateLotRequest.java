package com.pierr.thyrsio_api.stock.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Modification complète (PUT). L'article et le numéro de lot sont définitifs. */
public record UpdateLotRequest(
        LocalDate expiryDate,
        @Size(max = 1000) String notes
) {
}
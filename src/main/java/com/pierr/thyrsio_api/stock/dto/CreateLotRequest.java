package com.pierr.thyrsio_api.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateLotRequest(
        @NotNull UUID articleId,
        // Le numéro tel qu'il figure sur la livraison : on garde sa casse d'origine
        @NotBlank @Size(max = 100) String lotNumber,
        LocalDate expiryDate,
        @Size(max = 1000) String notes
) {
}
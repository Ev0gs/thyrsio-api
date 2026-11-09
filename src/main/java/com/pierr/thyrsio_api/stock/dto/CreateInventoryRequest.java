package com.pierr.thyrsio_api.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateInventoryRequest(
        @NotBlank @Size(max = 100) String label,
        @PastOrPresent Instant countedAt,   // facultatif : maintenant par défaut
        @Size(max = 1000) String notes,
        Boolean prefill                     // true : une ligne par stock non nul
) {
}
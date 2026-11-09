package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.stock.MovementType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateMovementRequest(
        @NotNull UUID articleId,
        UUID lotId,
        @NotNull MovementType type,
        // Positive pour une entrée ou une sortie ; signée pour un ajustement (+5 / -5)
        @NotNull @Digits(integer = 10, fraction = 4) BigDecimal quantity,
        // Facultatif, maintenant par défaut ; une date passée est permise, pas une date future
        @PastOrPresent Instant occurredAt,
        @Size(max = 100) String reference,
        @Size(max = 1000) String notes
) {
}
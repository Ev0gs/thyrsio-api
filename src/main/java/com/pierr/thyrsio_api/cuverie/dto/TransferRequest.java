package com.pierr.thyrsio_api.cuverie.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Du vin passe d'une cuve à une autre (soutirage, transfert).
 * volumeHl : ce qui arrive dans la cuve de destination.
 * lossHl : ce qui a été perdu en route (lies, fond de tuyau) ; la cuve d'origine baisse de volumeHl + lossHl.
 * emptySource : vider entièrement la cuve d'origine ; volumeHl est alors calculé (volume présent − perte).
 */
public record TransferRequest(
        @NotNull UUID sourceTankId,
        @NotNull UUID destinationTankId,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal volumeHl,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal lossHl,
        Boolean emptySource,
        @PastOrPresent Instant occurredAt,
        @Size(max = 1000) String notes
) {
}

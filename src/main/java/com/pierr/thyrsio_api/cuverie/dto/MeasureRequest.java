package com.pierr.thyrsio_api.cuverie.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un jaugeage : on saisit le volume réellement mesuré, l'application crée l'ajustement de l'écart.
 * wineLotId : seulement pour une cuve vue vide dans l'application mais qui contient du vin.
 */
public record MeasureRequest(
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal measuredHl,
        UUID wineLotId,
        @PastOrPresent Instant occurredAt,
        @Size(max = 1000) String notes
) {
}

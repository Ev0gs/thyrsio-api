package com.pierr.thyrsio_api.cuverie.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Une perte constatée dans une cuve : évaporation, fuite, lies retirées... */
public record LossRequest(
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal volumeHl,
        @PastOrPresent Instant occurredAt,
        @Size(max = 100) String reference,
        @Size(max = 1000) String notes
) {
}

package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.WineColor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un camion-citerne arrive : on crée le lot de vin et on remplit une ou plusieurs cuves, en une seule saisie.
 */
public record ReceptionRequest(
        @NotNull UUID supplierId,
        @NotBlank @Size(max = 200) String appellation,
        @NotNull WineColor color,
        // null : vin non millésimé
        @Min(1900) @Max(2100) Short vintage,
        @DecimalMin("0") @DecimalMax("25") @Digits(integer = 2, fraction = 2) BigDecimal alcohol,
        @Size(max = 100) String supplierLotNumber,
        @Size(max = 100) String daeNumber,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal pricePerHl,
        // Facultatif, maintenant par défaut ; pas dans le futur
        @PastOrPresent Instant receivedAt,
        @Size(max = 1000) String notes,
        // @Valid : chaque ligne est validée elle aussi
        @NotEmpty @Size(max = 20) List<@Valid @NotNull TankFill> fills
) {
}

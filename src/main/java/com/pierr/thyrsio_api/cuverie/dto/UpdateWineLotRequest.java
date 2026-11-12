package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.WineColor;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Correction complète de la fiche (PUT) : un champ facultatif vide efface la valeur.
 * Le code, le fournisseur et la date de réception ne changent pas : ils sont la trace de l'arrivée.
 */
public record UpdateWineLotRequest(
        @NotBlank @Size(max = 200) String appellation,
        @NotNull WineColor color,
        @Min(1900) @Max(2100) Short vintage,
        @DecimalMin("0") @DecimalMax("25") @Digits(integer = 2, fraction = 2) BigDecimal alcohol,
        @Size(max = 100) String supplierLotNumber,
        @Size(max = 100) String daeNumber,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal pricePerHl,
        @Size(max = 1000) String notes
) {
}

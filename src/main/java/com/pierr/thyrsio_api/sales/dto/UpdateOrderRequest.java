package com.pierr.thyrsio_api.sales.dto;

import com.pierr.thyrsio_api.referentiel.Incoterm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Modification complète (PUT). Le client ne change pas : pour un autre client, c'est une autre commande. */
public record UpdateOrderRequest(
        UUID deliveryAddressId,
        @Size(max = 100) String customerReference,
        @NotNull LocalDate orderDate,
        LocalDate requestedLoadingDate,
        Incoterm incoterm,
        @Size(max = 2000) String notes,
        @Size(max = 100) List<@Valid @NotNull OrderLineRequest> lines
) {
}

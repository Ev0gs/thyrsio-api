package com.pierr.thyrsio_api.sales.dto;

import com.pierr.thyrsio_api.referentiel.Incoterm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Une nouvelle commande, en brouillon.
 * deliveryAddressId absent : l'adresse par défaut du client. incoterm absent : celui du client.
 * orderDate absente : aujourd'hui.
 */
public record CreateOrderRequest(
        @NotNull UUID clientId,
        UUID deliveryAddressId,
        @Size(max = 100) String customerReference,
        LocalDate orderDate,
        LocalDate requestedLoadingDate,
        Incoterm incoterm,
        @Size(max = 2000) String notes,
        @Size(max = 100) List<@Valid @NotNull OrderLineRequest> lines
) {
}

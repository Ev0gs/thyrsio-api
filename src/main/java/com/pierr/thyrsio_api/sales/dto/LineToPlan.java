package com.pierr.thyrsio_api.sales.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Une ligne de commande confirmée qui n'est couverte ni par le stock ni par une mise : il faut planifier. */
public record LineToPlan(
        UUID lineId,
        UUID orderId,
        String orderCode,
        String clientName,
        UUID articleId,
        String articleCode,
        String articleLabel,
        LocalDate requestedLoadingDate,
        int remaining,
        int fromStock,
        int fromBottlings,
        int missing
) {
}

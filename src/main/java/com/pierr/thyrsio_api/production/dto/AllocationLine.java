package com.pierr.thyrsio_api.production.dto;

import java.util.UUID;

/** Une commande servie par la mise : « CC-26-004 · Tokyo Wine Import · 3 600 bt ». */
public record AllocationLine(
        UUID orderLineId,
        UUID orderId,
        String orderCode,
        String clientName,
        int quantity
) {
}

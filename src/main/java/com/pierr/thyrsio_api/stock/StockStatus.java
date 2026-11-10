package com.pierr.thyrsio_api.stock;

import java.math.BigDecimal;

/** L'état d'un article en stock, calculé à partir du total et du seuil d'alerte. */
public enum StockStatus {
    OK,
    BELOW_THRESHOLD,
    OUT_OF_STOCK;

    public static StockStatus of(BigDecimal quantity, BigDecimal alertThreshold) {
        if (quantity.signum() == 0) {
            return OUT_OF_STOCK;
        }
        if (alertThreshold != null && quantity.compareTo(alertThreshold) < 0) {
            return BELOW_THRESHOLD;
        }
        return OK;
    }
}
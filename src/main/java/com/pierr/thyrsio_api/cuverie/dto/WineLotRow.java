package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.WineColor;
import com.pierr.thyrsio_api.cuverie.WineLot;
import com.pierr.thyrsio_api.referentiel.SupplierInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Une ligne de la liste des lots de vin. */
public record WineLotRow(
        UUID id,
        String code,
        UUID supplierId,
        String supplierName,
        String appellation,
        WineColor color,
        Short vintage,
        BigDecimal alcohol,
        Instant receivedAt,
        // Ce qui reste en cuve aujourd'hui, et dans combien de cuves
        BigDecimal volumeHl,
        long tankCount
) {
    public static WineLotRow from(WineLot lot, SupplierInfo supplier, BigDecimal volumeHl, long tankCount) {
        return new WineLotRow(
                lot.getId(),
                lot.getCode(),
                lot.getSupplierId(),
                supplier != null ? supplier.name() : null,
                lot.getAppellation(),
                lot.getColor(),
                lot.getVintage(),
                lot.getAlcohol(),
                lot.getReceivedAt(),
                volumeHl,
                tankCount
        );
    }
}

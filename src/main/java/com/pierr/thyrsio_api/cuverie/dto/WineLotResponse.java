package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.Tank;
import com.pierr.thyrsio_api.cuverie.WineColor;
import com.pierr.thyrsio_api.cuverie.WineLot;
import com.pierr.thyrsio_api.referentiel.SupplierInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La fiche complète d'un lot, avec les cuves qui le contiennent aujourd'hui. */
public record WineLotResponse(
        UUID id,
        String code,
        UUID supplierId,
        String supplierName,
        String appellation,
        WineColor color,
        Short vintage,
        BigDecimal alcohol,
        String supplierLotNumber,
        String daeNumber,
        BigDecimal pricePerHl,
        Instant receivedAt,
        String notes,
        BigDecimal volumeHl,
        List<TankContent> tanks,
        UUID createdBy,
        Instant createdAt
) {
    /** Une cuve qui contient ce lot, et combien. */
    public record TankContent(UUID tankId, String tankCode, BigDecimal volumeHl, BigDecimal capacityHl) {
    }

    public static WineLotResponse from(WineLot lot, SupplierInfo supplier, List<Tank> tanks) {
        List<TankContent> contents = tanks.stream()
                .map(tank -> new TankContent(tank.getId(), tank.getCode(), tank.getVolumeHl(), tank.getCapacityHl()))
                .toList();
        BigDecimal total = contents.stream().map(TankContent::volumeHl).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WineLotResponse(
                lot.getId(),
                lot.getCode(),
                lot.getSupplierId(),
                supplier != null ? supplier.name() : null,
                lot.getAppellation(),
                lot.getColor(),
                lot.getVintage(),
                lot.getAlcohol(),
                lot.getSupplierLotNumber(),
                lot.getDaeNumber(),
                lot.getPricePerHl(),
                lot.getReceivedAt(),
                lot.getNotes(),
                total,
                contents,
                lot.getCreatedBy(),
                lot.getCreatedAt()
        );
    }
}

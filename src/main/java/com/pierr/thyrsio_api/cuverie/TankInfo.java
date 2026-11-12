package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.cuverie.dto.WineLotSummary;

import java.math.BigDecimal;
import java.util.UUID;

/** Ce que les autres modules ont le droit de savoir d'une cuve. wineLot est null si elle est vide. */
public record TankInfo(
        UUID id,
        String code,
        BigDecimal capacityHl,
        BigDecimal volumeHl,
        TankStatus status,
        boolean active,
        WineLotSummary wineLot
) {
    static TankInfo from(Tank tank) {
        return new TankInfo(tank.getId(), tank.getCode(), tank.getCapacityHl(), tank.getVolumeHl(),
                tank.getStatus(), tank.isActive(), WineLotSummary.from(tank.getWineLot()));
    }
}

package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.Tank;
import com.pierr.thyrsio_api.cuverie.TankState;
import com.pierr.thyrsio_api.cuverie.TankStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TankResponse(
        UUID id,
        String code,
        String label,
        BigDecimal capacityHl,
        BigDecimal volumeHl,
        BigDecimal freeSpaceHl,
        TankStatus status,
        // Calculé : EMPTY / IN_USE selon le volume, ou le statut manuel
        TankState state,
        WineLotSummary wineLot,
        boolean active,
        Instant updatedAt
) {
    public static TankResponse from(Tank tank) {
        return new TankResponse(
                tank.getId(),
                tank.getCode(),
                tank.getLabel(),
                tank.getCapacityHl(),
                tank.getVolumeHl(),
                tank.freeSpaceHl(),
                tank.getStatus(),
                tank.getState(),
                WineLotSummary.from(tank.getWineLot()),
                tank.isActive(),
                tank.getUpdatedAt()
        );
    }
}

package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.TankMovement;
import com.pierr.thyrsio_api.cuverie.TankMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TankMovementResponse(
        UUID id,
        UUID tankId,
        String tankCode,
        WineLotSummary wineLot,
        TankMovementType type,
        BigDecimal volumeHl,
        UUID transferId,
        Instant occurredAt,
        String reference,
        String notes,
        UUID createdBy,
        Instant createdAt
) {
    public static TankMovementResponse from(TankMovement movement) {
        return new TankMovementResponse(
                movement.getId(),
                movement.getTank().getId(),
                movement.getTank().getCode(),
                WineLotSummary.from(movement.getWineLot()),
                movement.getType(),
                movement.getVolumeHl(),
                movement.getTransferId(),
                movement.getOccurredAt(),
                movement.getReference(),
                movement.getNotes(),
                movement.getCreatedBy(),
                movement.getCreatedAt()
        );
    }
}

package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.stock.Lot;
import com.pierr.thyrsio_api.stock.MovementType;
import com.pierr.thyrsio_api.stock.StockMovement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MovementResponse(
        UUID id,
        UUID articleId,
        String articleCode,
        String unitCode,
        UUID lotId,
        String lotNumber,
        MovementType type,
        BigDecimal quantity,
        Instant occurredAt,
        String reference,
        String notes,
        UUID createdBy,
        Instant createdAt
) {
    public static MovementResponse from(StockMovement movement, ArticleInfo article) {
        Lot lot = movement.getLot();
        return new MovementResponse(
                movement.getId(),
                movement.getArticleId(),
                article.code(),
                article.unitCode(),
                lot != null ? lot.getId() : null,
                lot != null ? lot.getLotNumber() : null,
                movement.getType(),
                movement.getQuantity(),
                movement.getOccurredAt(),
                movement.getReference(),
                movement.getNotes(),
                movement.getCreatedBy(),
                movement.getCreatedAt()
        );
    }
}
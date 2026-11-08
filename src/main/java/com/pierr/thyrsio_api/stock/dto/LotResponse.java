package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.stock.Lot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LotResponse(
        UUID id,
        UUID articleId,
        String articleCode,
        String articleLabel,
        String lotNumber,
        LocalDate expiryDate,
        String notes,
        Instant createdAt
) {
    public static LotResponse from(Lot lot, ArticleInfo article) {
        return new LotResponse(
                lot.getId(),
                lot.getArticleId(),
                article.code(),
                article.label(),
                lot.getLotNumber(),
                lot.getExpiryDate(),
                lot.getNotes(),
                lot.getCreatedAt()
        );
    }
}
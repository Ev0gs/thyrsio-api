package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.stock.Lot;
import com.pierr.thyrsio_api.stock.StockBalance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID articleId,
        String articleCode,
        String articleLabel,
        String unitCode,
        UUID lotId,
        String lotNumber,
        BigDecimal quantity,
        Instant updatedAt
) {
    public static BalanceResponse from(StockBalance balance, ArticleInfo article) {
        Lot lot = balance.getLot();
        return new BalanceResponse(
                balance.getArticleId(),
                article.code(),
                article.label(),
                article.unitCode(),
                lot != null ? lot.getId() : null,
                lot != null ? lot.getLotNumber() : null,
                balance.getQuantity(),
                balance.getUpdatedAt()
        );
    }
}
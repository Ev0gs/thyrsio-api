package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.stock.StockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Une ligne de la vue d'ensemble : un article, tous lots confondus. */
public record StockOverviewRow(
        UUID articleId,
        String articleCode,
        String articleLabel,
        String categoryCode,
        String categoryLabel,
        String unitCode,
        boolean lotTracked,
        BigDecimal quantity,
        BigDecimal alertThreshold,
        long lotsInStock,
        StockStatus status,
        Instant lastMovementAt,   // null : aucun mouvement
        boolean active
) {
    public static StockOverviewRow of(ArticleInfo article, BigDecimal quantity, long lotsInStock, Instant lastMovementAt) {
        return new StockOverviewRow(
                article.id(),
                article.code(),
                article.label(),
                article.categoryCode(),
                article.categoryLabel(),
                article.unitCode(),
                article.lotTracked(),
                quantity,
                article.alertThreshold(),
                lotsInStock,
                StockStatus.of(quantity, article.alertThreshold()),
                lastMovementAt,
                article.active()
        );
    }
}
package com.pierr.thyrsio_api.stock.dto;

import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.stock.InventoryLine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * theoreticalQuantity : en brouillon, le stock actuel ; une fois validé, le stock figé à la validation.
 * difference : compté - théorique (en brouillon, un aperçu ; une fois validé, l'écart réellement appliqué).
 */
public record InventoryLineResponse(
        UUID id,
        UUID articleId,
        String articleCode,
        String articleLabel,
        String unitCode,
        UUID lotId,
        String lotNumber,
        BigDecimal theoreticalQuantity,
        BigDecimal countedQuantity,
        BigDecimal difference,
        String notes
) {
    public static InventoryLineResponse from(InventoryLine line, ArticleInfo article,
                                             BigDecimal theoretical, BigDecimal difference) {
        return new InventoryLineResponse(
                line.getId(),
                line.getArticleId(),
                article.code(),
                article.label(),
                article.unitCode(),
                line.getLotId(),
                line.getLot() != null ? line.getLot().getLotNumber() : null,
                theoretical,
                line.getCountedQuantity(),
                difference,
                line.getNotes()
        );
    }
}
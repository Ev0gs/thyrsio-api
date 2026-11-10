package com.pierr.thyrsio_api.stock;

import java.math.BigDecimal;
import java.util.UUID;

/** Résultat d'un GROUP BY : le stock total d'un article et le nombre de ses lots non vides. */
public record ArticleStockTotal(UUID articleId, BigDecimal quantity, Long lotsInStock) {
}
package com.pierr.thyrsio_api.stock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ce que les autres modules peuvent lire du stock, sans toucher aux soldes. */
@Service
public class StockLookup {

    private final StockBalanceRepository balanceRepository;
    private final LotRepository lotRepository;

    public StockLookup(StockBalanceRepository balanceRepository, LotRepository lotRepository) {
        this.balanceRepository = balanceRepository;
        this.lotRepository = lotRepository;
    }

    /** Le stock total de chaque article, tous lots confondus. Un article absent de la Map n'a pas de stock. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> totalsByArticle(Collection<UUID> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        return balanceRepository.totalsByArticle(articleIds).stream()
                .collect(Collectors.toMap(ArticleStockTotal::articleId, ArticleStockTotal::quantity));
    }

    /** Le numéro de plusieurs lots, en une requête, indexé par id de lot. */
    @Transactional(readOnly = true)
    public Map<UUID, String> lotNumbers(Collection<UUID> lotIds) {
        if (lotIds.isEmpty()) {
            return Map.of();
        }
        return lotRepository.findAllById(lotIds).stream()
                .collect(Collectors.toMap(Lot::getId, Lot::getLotNumber));
    }
}

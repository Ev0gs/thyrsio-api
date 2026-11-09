package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.referentiel.ArticleStockUsage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/** La réponse du stock à la question du référentiel. Package-private : personne ne l'appelle directement. */
@Component
class StockArticleUsage implements ArticleStockUsage {

    private final StockMovementRepository movementRepository;
    private final LotRepository lotRepository;

    StockArticleUsage(StockMovementRepository movementRepository, LotRepository lotRepository) {
        this.movementRepository = movementRepository;
        this.lotRepository = lotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsedInStock(Collection<UUID> articleIds) {
        if (articleIds.isEmpty()) {
            return false;
        }
        // Un lot sans mouvement compte aussi : il a été créé pour un article suivi par lot
        return movementRepository.existsByArticleIdIn(articleIds)
                || lotRepository.existsByArticleIdIn(articleIds);
    }
}
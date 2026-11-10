package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.referentiel.ArticleLookup;
import com.pierr.thyrsio_api.stock.dto.StockOverviewRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockOverviewService {

    private final ArticleLookup articleLookup;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;

    public StockOverviewService(ArticleLookup articleLookup,
                                StockBalanceRepository balanceRepository,
                                StockMovementRepository movementRepository) {
        this.articleLookup = articleLookup;
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
    }

    /** Une ligne par article, y compris ceux qui n'ont jamais eu de stock (ruptures). 3 requêtes au total. */
    @Transactional(readOnly = true)
    public List<StockOverviewRow> overview(boolean includeArchived) {
        List<ArticleInfo> articles = articleLookup.findAll(includeArchived);
        if (articles.isEmpty()) {
            return List.of();
        }
        List<UUID> articleIds = articles.stream().map(ArticleInfo::id).toList();

        Map<UUID, ArticleStockTotal> totals = balanceRepository.totalsByArticle(articleIds).stream()
                .collect(Collectors.toMap(ArticleStockTotal::articleId, Function.identity()));
        Map<UUID, Instant> lastMovements = movementRepository.lastMovementByArticle(articleIds).stream()
                .collect(Collectors.toMap(ArticleLastMovement::articleId, ArticleLastMovement::occurredAt));

        return articles.stream()
                .map(article -> {
                    ArticleStockTotal total = totals.get(article.id());  // null : aucun stock
                    return StockOverviewRow.of(
                            article,
                            total != null ? total.quantity() : BigDecimal.ZERO,
                            total != null ? total.lotsInStock() : 0,
                            lastMovements.get(article.id()));
                })
                .toList();
    }
}
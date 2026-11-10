package com.pierr.thyrsio_api.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID>,
        JpaSpecificationExecutor<StockBalance> {

    // PESSIMISTIC_WRITE = SELECT ... FOR UPDATE : une autre transaction qui veut ce solde
    // attend la fin de la nôtre. Le mot "ForUpdate" est décoratif : Spring ignore ce qui est
    // entre "find" et "By", il sert juste à rendre le verrou visible à la lecture.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockBalance> findForUpdateByArticleIdAndLotIsNull(UUID articleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockBalance> findForUpdateByArticleIdAndLot(UUID articleId, Lot lot);

    @Override
    @EntityGraph(attributePaths = "lot")
    Page<StockBalance> findAll(Specification<StockBalance> spec, Pageable pageable);

    // Stock théorique des lignes d'inventaire, en une requête
    List<StockBalance> findByArticleIdIn(Collection<UUID> articleIds);

    // Pré-remplissage : un solde non nul = une ligne à compter
    List<StockBalance> findByQuantityGreaterThan(BigDecimal quantity);

    /**
     * Le calcul est fait par la base (SUM ... GROUP BY) : une seule ligne par article remonte.
     * count(b.lot) ne compte que les soldes qui ont un lot.
     */
    @Query("""
            select new com.pierr.thyrsio_api.stock.ArticleStockTotal(b.articleId, sum(b.quantity), count(b.lot))
              from StockBalance b
             where b.articleId in :articleIds and b.quantity > 0
             group by b.articleId
            """)
    List<ArticleStockTotal> totalsByArticle(@Param("articleIds") Collection<UUID> articleIds);
}
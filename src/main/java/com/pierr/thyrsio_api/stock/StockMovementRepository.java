package com.pierr.thyrsio_api.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>,
        JpaSpecificationExecutor<StockMovement> {

    @Override
    @EntityGraph(attributePaths = "lot")
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    boolean existsByArticleIdIn(Collection<UUID> articleIds);

    @Query("""
            select new com.pierr.thyrsio_api.stock.ArticleLastMovement(m.articleId, max(m.occurredAt))
              from StockMovement m
             where m.articleId in :articleIds
             group by m.articleId
            """)
    List<ArticleLastMovement> lastMovementByArticle(@Param("articleIds") Collection<UUID> articleIds);
}
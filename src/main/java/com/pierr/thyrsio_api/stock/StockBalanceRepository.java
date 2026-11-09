package com.pierr.thyrsio_api.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

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
}
package com.pierr.thyrsio_api.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LotRepository extends JpaRepository<Lot, UUID> {

    boolean existsByArticleIdAndLotNumber(UUID articleId, String lotNumber);

    List<Lot> findByArticleIdOrderByCreatedAtDesc(UUID articleId);

    boolean existsByArticleIdIn(Collection<UUID> articleIds);
}
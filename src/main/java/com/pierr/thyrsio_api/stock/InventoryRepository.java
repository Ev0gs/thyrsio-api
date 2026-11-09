package com.pierr.thyrsio_api.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    // L'inventaire, ses lignes et leurs lots en une requête (sinon une requête par lot)
    @EntityGraph(attributePaths = {"lines", "lines.lot"})
    Optional<Inventory> findWithLinesById(UUID id);

    Page<Inventory> findByStatus(InventoryStatus status, Pageable pageable);
}
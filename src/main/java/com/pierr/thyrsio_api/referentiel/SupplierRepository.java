package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    // Query methods : "WHERE active = true", "WHERE kind = ?", avec pagination
    Page<Supplier> findByActiveTrue(Pageable pageable);

    Page<Supplier> findByKind(SupplierKind kind, Pageable pageable);

    Page<Supplier> findByActiveTrueAndKind(SupplierKind kind, Pageable pageable);
}

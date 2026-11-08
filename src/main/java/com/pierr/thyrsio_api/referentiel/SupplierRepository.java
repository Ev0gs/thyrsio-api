package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    // Query method : "WHERE active = true", avec pagination
    Page<Supplier> findByActiveTrue(Pageable pageable);
}

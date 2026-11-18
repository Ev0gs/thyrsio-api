package com.pierr.thyrsio_api.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BottlingProductionRepository extends JpaRepository<BottlingProduction, UUID> {

    /** Les productions planifiées : le calcul des manquants les reprend dans l'ordre où elles consommeront. */
    @Query("select p from BottlingProduction p join fetch p.bottling where p.status = :status")
    List<BottlingProduction> findByStatusWithBottling(@Param("status") ProductionStatus status);

    boolean existsByLotNumber(String lotNumber);
}

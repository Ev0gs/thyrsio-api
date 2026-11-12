package com.pierr.thyrsio_api.cuverie;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TankMovementRepository extends JpaRepository<TankMovement, UUID>,
        JpaSpecificationExecutor<TankMovement> {

    // La cuve et le lot de chaque mouvement sont chargés avec la page : pas de N+1
    @Override
    @EntityGraph(attributePaths = {"tank", "wineLot"})
    Page<TankMovement> findAll(Specification<TankMovement> spec, Pageable pageable);
}

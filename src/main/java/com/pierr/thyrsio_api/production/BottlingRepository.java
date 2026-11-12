package com.pierr.thyrsio_api.production;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BottlingRepository extends JpaRepository<Bottling, UUID>, JpaSpecificationExecutor<Bottling> {

    // Les matières en même temps que la mise. Pas les consommations : charger deux listes
    // dans une seule requête est refusé par Hibernate (MultipleBagFetchException)
    @EntityGraph(attributePaths = "materials")
    @Query("select b from Bottling b where b.id = :id")
    Optional<Bottling> findWithMaterialsById(@Param("id") UUID id);

    /** Les mises planifiées, dans l'ordre où elles consommeront le stock. */
    @EntityGraph(attributePaths = "materials")
    List<Bottling> findByStatusOrderByStartDateAscCreatedAtAsc(BottlingStatus status);

    @Query("select b.code from Bottling b where b.code like :pattern")
    List<String> findCodesLike(@Param("pattern") String pattern);

    @Query("select b.lotNumber from Bottling b where b.lotNumber like :pattern")
    List<String> findLotNumbersLike(@Param("pattern") String pattern);
}

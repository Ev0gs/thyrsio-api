package com.pierr.thyrsio_api.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BottlingAllocationRepository extends JpaRepository<BottlingAllocation, UUID> {

    /** Les allocations de ces lignes de commande, avec leur production et leur mise, productions annulées exclues. */
    @Query("""
            select a from BottlingAllocation a join fetch a.production p join fetch p.bottling
             where a.orderLineId in :lineIds and p.status <> :excluded
            """)
    List<BottlingAllocation> findForOrderLines(@Param("lineIds") Collection<UUID> lineIds,
                                               @Param("excluded") ProductionStatus excluded);
}

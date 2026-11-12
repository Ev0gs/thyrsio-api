package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.sales.PlannedSupply;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Ce que les mises prévoient pour les commandes : l'implémentation de l'interface du module Ventes. */
@Component
class ProductionPlannedSupply implements PlannedSupply {

    private final BottlingAllocationRepository allocationRepository;

    ProductionPlannedSupply(BottlingAllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Allocation> allocationsFor(Collection<UUID> orderLineIds) {
        if (orderLineIds.isEmpty()) {
            return List.of();
        }
        return allocationRepository.findForOrderLines(orderLineIds, BottlingStatus.CANCELLED).stream()
                .map(a -> new Allocation(a.getOrderLineId(), a.getBottling().getId(), a.getBottling().getCode(),
                        a.getBottling().getStartDate(), a.getBottling().getStatus().name(), a.getQuantity()))
                .toList();
    }
}

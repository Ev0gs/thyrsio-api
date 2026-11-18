package com.pierr.thyrsio_api.production;

import jakarta.persistence.*;

import java.util.UUID;

/** Une part d'une production réservée à une ligne de commande. Ne se modifie qu'à travers la mise. */
@Entity
@Table(name = "bottling_allocations")
public class BottlingAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_id", updatable = false)
    private BottlingProduction production;

    // UUID : la ligne de commande appartient au module Ventes
    @Column(nullable = false, updatable = false)
    private UUID orderLineId;

    @Column(nullable = false)
    private int quantity;

    protected BottlingAllocation() {
    }

    BottlingAllocation(BottlingProduction production, UUID orderLineId, int quantity) {
        this.production = production;
        this.orderLineId = orderLineId;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }

    public BottlingProduction getProduction() { return production; }

    public UUID getOrderLineId() { return orderLineId; }

    public int getQuantity() { return quantity; }

    void setQuantity(int quantity) { this.quantity = quantity; }
}

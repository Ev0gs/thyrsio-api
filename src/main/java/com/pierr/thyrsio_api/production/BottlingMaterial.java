package com.pierr.thyrsio_api.production;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Une matière sèche prévue pour une production, et sa quantité. Ne se modifie qu'à travers la mise. */
@Entity
@Table(name = "bottling_materials")
public class BottlingMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_id", updatable = false)
    private BottlingProduction production;

    // UUID : l'article appartient au module Référentiel
    @Column(nullable = false, updatable = false)
    private UUID articleId;

    @Column(nullable = false)
    private BigDecimal plannedQuantity;

    protected BottlingMaterial() {
    }

    BottlingMaterial(BottlingProduction production, UUID articleId, BigDecimal plannedQuantity) {
        this.production = production;
        this.articleId = articleId;
        this.plannedQuantity = plannedQuantity;
    }

    public UUID getId() { return id; }

    public UUID getArticleId() { return articleId; }

    public BigDecimal getPlannedQuantity() { return plannedQuantity; }

    void setPlannedQuantity(BigDecimal plannedQuantity) { this.plannedQuantity = plannedQuantity; }
}

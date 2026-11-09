package com.pierr.thyrsio_api.stock;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable // Hibernate n'enverra jamais d'UPDATE : un mouvement ne se modifie pas, il se compense
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // UUID : l'article appartient au module Référentiel
    @Column(nullable = false)
    private UUID articleId;

    // Relation JPA : le lot appartient au même module
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant occurredAt;

    private String reference;

    private String notes;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(UUID articleId, Lot lot, MovementType type, BigDecimal signedQuantity,
                         Instant occurredAt, String reference, String notes, UUID createdBy) {
        this.articleId = articleId;
        this.lot = lot;
        this.type = type;
        this.quantity = signedQuantity;
        this.occurredAt = occurredAt;
        this.reference = reference;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }

    public UUID getArticleId() { return articleId; }

    public Lot getLot() { return lot; }

    public MovementType getType() { return type; }

    public BigDecimal getQuantity() { return quantity; }

    public Instant getOccurredAt() { return occurredAt; }

    public String getReference() { return reference; }

    public String getNotes() { return notes; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
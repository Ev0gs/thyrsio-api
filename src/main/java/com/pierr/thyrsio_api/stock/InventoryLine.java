package com.pierr.thyrsio_api.stock;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory_lines")
public class InventoryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    @Column(nullable = false, updatable = false)
    private UUID articleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", updatable = false)
    private Lot lot;

    private BigDecimal countedQuantity;

    private BigDecimal expectedQuantity;

    private BigDecimal adjustment;

    private String notes;

    protected InventoryLine() {
    }

    /** Package-private : seul Inventory.addLine crée une ligne. */
    InventoryLine(Inventory inventory, UUID articleId, Lot lot) {
        this.inventory = inventory;
        this.articleId = articleId;
        this.lot = lot;
    }

    boolean concerns(UUID articleId, UUID lotId) {
        return this.articleId.equals(articleId) && Objects.equals(getLotId(), lotId);
    }

    void setCount(BigDecimal countedQuantity, String notes) {
        this.countedQuantity = countedQuantity;
        this.notes = notes;
    }

    /** Fige le résultat à la validation. */
    void recordResult(BigDecimal expectedQuantity, BigDecimal adjustment) {
        this.expectedQuantity = expectedQuantity;
        this.adjustment = adjustment;
    }

    public UUID getId() { return id; }

    public UUID getArticleId() { return articleId; }

    public Lot getLot() { return lot; }

    public UUID getLotId() { return lot != null ? lot.getId() : null; }

    public BigDecimal getCountedQuantity() { return countedQuantity; }

    public BigDecimal getExpectedQuantity() { return expectedQuantity; }

    public BigDecimal getAdjustment() { return adjustment; }

    public String getNotes() { return notes; }
}
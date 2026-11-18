package com.pierr.thyrsio_api.production;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Ce qui a réellement été consommé lors de la mise, par article. Figé à la clôture. */
@Entity
@Table(name = "bottling_consumptions")
public class BottlingConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bottling_id", updatable = false)
    private Bottling bottling;

    @Column(nullable = false, updatable = false)
    private UUID articleId;

    @Column(nullable = false, updatable = false)
    private BigDecimal quantity;

    protected BottlingConsumption() {
    }

    BottlingConsumption(Bottling bottling, UUID articleId, BigDecimal quantity) {
        this.bottling = bottling;
        this.articleId = articleId;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }

    public UUID getArticleId() { return articleId; }

    public BigDecimal getQuantity() { return quantity; }
}

package com.pierr.thyrsio_api.stock;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_balances")
public class StockBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID articleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", updatable = false)
    private Lot lot;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant updatedAt;

    protected StockBalance() {
    }

    public StockBalance(UUID articleId, Lot lot) {
        this.articleId = articleId;
        this.lot = lot;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getArticleId() { return articleId; }

    public Lot getLot() { return lot; }

    public BigDecimal getQuantity() { return quantity; }

    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Package-private : seul StockService fait bouger un solde, toujours avec un mouvement.
     * Le service vérifie le stock disponible avant ; ceci n'est qu'un filet de sécurité.
     */
    void apply(BigDecimal signedQuantity) {
        BigDecimal next = quantity.add(signedQuantity);
        if (next.signum() < 0) {
            throw new IllegalStateException("Solde négatif refusé pour l'article " + articleId);
        }
        this.quantity = next;
        this.updatedAt = Instant.now();
    }
}
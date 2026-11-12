package com.pierr.thyrsio_api.sales;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Un produit fini commandé, et combien en est déjà parti. Ne se modifie qu'à travers la commande. */
@Entity
@Table(name = "customer_order_lines")
public class CustomerOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", updatable = false)
    private CustomerOrder order;

    // UUID : l'article (produit fini) appartient au module Référentiel
    @Column(nullable = false, updatable = false)
    private UUID articleId;

    @Column(nullable = false)
    private int quantity;

    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int shippedQuantity;

    protected CustomerOrderLine() {
    }

    CustomerOrderLine(CustomerOrder order, UUID articleId, int quantity, BigDecimal unitPrice) {
        this.order = order;
        this.articleId = articleId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void change(int quantity, BigDecimal unitPrice) {
        if (quantity < shippedQuantity) {
            throw new BusinessRuleException(shippedQuantity + " bouteilles sont déjà parties : la quantité ne peut pas être inférieure");
        }
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void ship(int bottles) {
        if (bottles > remaining()) {
            throw new BusinessRuleException("Il ne reste que " + remaining() + " bouteilles à expédier sur cette ligne");
        }
        this.shippedQuantity += bottles;
    }

    /** Ce qui reste à expédier. */
    public int remaining() {
        return quantity - shippedQuantity;
    }

    public UUID getId() { return id; }

    public CustomerOrder getOrder() { return order; }

    public UUID getArticleId() { return articleId; }

    public int getQuantity() { return quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }

    public int getShippedQuantity() { return shippedQuantity; }
}

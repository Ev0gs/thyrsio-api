package com.pierr.thyrsio_api.shipping;

import jakarta.persistence.*;

import java.util.UUID;

/** Ce qui est chargé : une ligne de commande, un lot de bouteilles, une quantité. */
@Entity
@Table(name = "shipment_lines")
public class ShipmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", updatable = false)
    private Shipment shipment;

    // UUID : la ligne de commande (Ventes), l'article (Référentiel) et le lot (Stock) appartiennent à d'autres modules
    @Column(nullable = false, updatable = false)
    private UUID orderLineId;

    @Column(nullable = false, updatable = false)
    private UUID articleId;

    @Column(updatable = false)
    private UUID lotId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    protected ShipmentLine() {
    }

    ShipmentLine(Shipment shipment, UUID orderLineId, UUID articleId, UUID lotId, int quantity) {
        this.shipment = shipment;
        this.orderLineId = orderLineId;
        this.articleId = articleId;
        this.lotId = lotId;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }

    public UUID getOrderLineId() { return orderLineId; }

    public UUID getArticleId() { return articleId; }

    public UUID getLotId() { return lotId; }

    public int getQuantity() { return quantity; }
}

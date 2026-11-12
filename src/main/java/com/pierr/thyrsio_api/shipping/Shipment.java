package com.pierr.thyrsio_api.shipping;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Une expédition : préparée, puis partie. Une fois partie, elle et son bon de livraison sont figés. */
@Entity
@Table(name = "shipments")
public class Shipment {

    /** Une ligne voulue : ligne de commande, article, lot, quantité. */
    record LineValues(UUID orderLineId, UUID articleId, UUID lotId, int quantity) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    // UUID : la commande appartient au module Ventes
    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.DRAFT;

    @Column(nullable = false)
    private LocalDate plannedDate;

    private String carrier;

    private String transportReference;

    private String notes;

    private String deliveryAddressText;

    private Instant shippedAt;

    private UUID shippedBy;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ShipmentLine> lines = new ArrayList<>();

    protected Shipment() {
    }

    Shipment(String code, UUID orderId, UUID createdBy) {
        this.code = code;
        this.orderId = orderId;
        this.createdBy = createdBy;
    }

    void describe(LocalDate plannedDate, String carrier, String transportReference, String notes) {
        requireDraft();
        this.plannedDate = plannedDate;
        this.carrier = carrier;
        this.transportReference = transportReference;
        this.notes = notes;
        touch();
    }

    /**
     * Remplace les lignes. Une ligne d'expédition ne se modifie pas (on change de lot, on change de ligne) :
     * on vide puis on recrée, après un flush pour que les suppressions passent avant les insertions
     * (la contrainte UNIQUE (expédition, ligne, lot) refuserait sinon une ligne identique recréée).
     */
    void clearLines() {
        requireDraft();
        lines.clear();
        touch();
    }

    void addLines(List<LineValues> values) {
        requireDraft();
        values.forEach(v -> lines.add(new ShipmentLine(this, v.orderLineId(), v.articleId(), v.lotId(), v.quantity())));
        touch();
    }

    void markShipped(String deliveryAddressText, UUID userId) {
        requireDraft();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("L'expédition " + code + " ne contient aucune ligne");
        }
        this.status = ShipmentStatus.SHIPPED;
        this.deliveryAddressText = deliveryAddressText;
        this.shippedAt = Instant.now();
        this.shippedBy = userId;
        touch();
    }

    void cancel() {
        requireDraft();
        this.status = ShipmentStatus.CANCELLED;
        touch();
    }

    void requireDraft() {
        if (status != ShipmentStatus.DRAFT) {
            throw new BusinessRuleException("L'expédition " + code + " n'est plus en préparation (" + status
                    + ") : elle ne peut plus être modifiée");
        }
    }

    public int totalBottles() {
        return lines.stream().mapToInt(ShipmentLine::getQuantity).sum();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public String getCode() { return code; }

    public UUID getOrderId() { return orderId; }

    public ShipmentStatus getStatus() { return status; }

    public LocalDate getPlannedDate() { return plannedDate; }

    public String getCarrier() { return carrier; }

    public String getTransportReference() { return transportReference; }

    public String getNotes() { return notes; }

    public String getDeliveryAddressText() { return deliveryAddressText; }

    public Instant getShippedAt() { return shippedAt; }

    public UUID getShippedBy() { return shippedBy; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public List<ShipmentLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
}

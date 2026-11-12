package com.pierr.thyrsio_api.cuverie;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Un vin acheté en vrac, tel qu'il est arrivé. Il peut être réparti dans plusieurs cuves. */
@Entity
@Table(name = "wine_lots")
public class WineLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Généré à la réception (V26-001) et jamais modifié : il est imprimé, noté, cité
    @Column(nullable = false, updatable = false)
    private String code;

    // UUID : le fournisseur appartient au module Référentiel
    @Column(nullable = false, updatable = false)
    private UUID supplierId;

    @Column(nullable = false)
    private String appellation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WineColor color;

    // Short : la colonne est un smallint ; null = vin non millésimé
    private Short vintage;

    private BigDecimal alcohol;

    private String supplierLotNumber;

    private String daeNumber;

    private BigDecimal pricePerHl;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    private String notes;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected WineLot() {
    }

    public WineLot(String code, UUID supplierId, String appellation, WineColor color,
                   Instant receivedAt, UUID createdBy) {
        this.code = code;
        this.supplierId = supplierId;
        this.appellation = appellation;
        this.color = color;
        this.receivedAt = receivedAt;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }

    public String getCode() { return code; }

    public UUID getSupplierId() { return supplierId; }

    public String getAppellation() { return appellation; }

    public WineColor getColor() { return color; }

    public Short getVintage() { return vintage; }

    public BigDecimal getAlcohol() { return alcohol; }

    public String getSupplierLotNumber() { return supplierLotNumber; }

    public String getDaeNumber() { return daeNumber; }

    public BigDecimal getPricePerHl() { return pricePerHl; }

    public Instant getReceivedAt() { return receivedAt; }

    public String getNotes() { return notes; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    // --- Corrections de la fiche (une faute de frappe sur le bon de livraison...) ---

    void setAppellation(String appellation) { this.appellation = appellation; }

    void setColor(WineColor color) { this.color = color; }

    void setVintage(Short vintage) { this.vintage = vintage; }

    void setAlcohol(BigDecimal alcohol) { this.alcohol = alcohol; }

    void setSupplierLotNumber(String supplierLotNumber) { this.supplierLotNumber = supplierLotNumber; }

    void setDaeNumber(String daeNumber) { this.daeNumber = daeNumber; }

    void setPricePerHl(BigDecimal pricePerHl) { this.pricePerHl = pricePerHl; }

    void setNotes(String notes) { this.notes = notes; }

    /** "V26-001 (Bordeaux Supérieur 2023)" : pour les messages. */
    public String describe() {
        return code + " (" + appellation + (vintage != null ? " " + vintage : "") + ")";
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

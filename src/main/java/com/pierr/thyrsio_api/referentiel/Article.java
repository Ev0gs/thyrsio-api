package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ArticleCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", updatable = false)
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_supplier_id")
    private Supplier mainSupplier;

    private String supplierReference;

    // BigDecimal : valeur décimale exacte, adaptée aux quantités
    @Column(precision = 14, scale = 4)
    private BigDecimal alertThreshold;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Article() {
    }

    public Article(String code, String label, ArticleCategory category, Unit unit) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.unit = unit;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public ArticleCategory getCategory() {
        return category;
    }

    public void setCategory(ArticleCategory category) {
        this.category = category;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public Supplier getMainSupplier() {
        return mainSupplier;
    }

    public void setMainSupplier(Supplier mainSupplier) {
        this.mainSupplier = mainSupplier;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public void setSupplierReference(String supplierReference) {
        this.supplierReference = supplierReference;
    }

    public BigDecimal getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(BigDecimal alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public void archive() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

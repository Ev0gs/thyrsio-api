package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "article_categories")
public class ArticleCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identifiant lisible (BOUTEILLE, VIN_VRAC...) : ne change jamais une fois créé
    @Column(nullable = false, updatable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_unit_id")
    private Unit defaultUnit;

    // Les articles de cette catégorie sont suivis par lot
    @Column(nullable = false)
    private boolean lotTracked;

    // Soumis à la réglementation (capsules CRD)
    @Column(nullable = false)
    private boolean regulated;

    // Une catégorie créée est toujours active
    @Column(nullable = false)
    private boolean active = true;

    protected ArticleCategory() {
    }

    public ArticleCategory(String code, String label, Unit defaultUnit, boolean lotTracked, boolean regulated) {
        this.code = code;
        this.label = label;
        this.defaultUnit = defaultUnit;
        this.lotTracked = lotTracked;
        this.regulated = regulated;
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

    public Unit getDefaultUnit() {
        return defaultUnit;
    }

    public boolean isLotTracked() {
        return lotTracked;
    }

    public boolean isRegulated() {
        return regulated;
    }

    public boolean isActive() {
        return active;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setDefaultUnit(Unit defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public void setLotTracked(boolean lotTracked) {
        this.lotTracked = lotTracked;
    }

    public void setRegulated(boolean regulated) {
        this.regulated = regulated;
    }

    /** Archive la catégorie : elle n'est plus proposée, mais l'historique qui l'utilise reste intact. */
    public void archive() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }
}

package com.pierr.thyrsio_api.gs1;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Un gencode (EAN-13) et ce qu'il désigne : une bouteille d'un produit fini, ou un groupé
 * (carton de 6, caisse bois de 6...). Son produit et son conditionnement sont définitifs une fois
 * attribués : un code-barres imprimé ne change pas de sens. Pour changer, on le retire et on en crée un autre.
 */
@Entity
@Table(name = "gtins")
public class Gtin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, unique = true)
    private String gtin;

    // UUID : null pour un gencode repris d'un historique, hors de nos préfixes
    @Column(updatable = false)
    private UUID prefixId;

    // UUID : l'article appartient au module Référentiel ; null tant que le gencode est réservé
    private UUID finishedArticleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GtinPackaging packaging;

    @Column(nullable = false)
    private int unitsPerPack;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GtinStatus status = GtinStatus.ACTIVE;

    private String notes;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant assignedAt;

    private Instant retiredAt;

    protected Gtin() {
    }

    Gtin(String gtin, UUID prefixId, GtinPackaging packaging, int unitsPerPack, String description,
         String notes, UUID createdBy) {
        this.gtin = gtin;
        this.prefixId = prefixId;
        this.packaging = packaging;
        this.unitsPerPack = unitsPerPack;
        this.description = description;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    /** Attribue le gencode à un produit fini : une seule fois. */
    void assign(UUID finishedArticleId, GtinPackaging packaging, int unitsPerPack) {
        requireActive();
        if (this.finishedArticleId != null) {
            throw new BusinessRuleException("Le gencode " + gtin + " est déjà attribué : "
                    + "retirez-le et créez-en un autre pour un autre produit");
        }
        this.finishedArticleId = finishedArticleId;
        this.packaging = packaging;
        this.unitsPerPack = unitsPerPack;
        this.assignedAt = Instant.now();
    }

    void describe(String description, String notes) {
        this.description = description;
        this.notes = notes;
    }

    void retire() {
        requireActive();
        this.status = GtinStatus.RETIRED;
        this.retiredAt = Instant.now();
    }

    private void requireActive() {
        if (status == GtinStatus.RETIRED) {
            throw new BusinessRuleException("Le gencode " + gtin + " est retiré : il ne sert plus");
        }
    }

    public UUID getId() { return id; }
    public String getGtin() { return gtin; }
    public UUID getPrefixId() { return prefixId; }
    public UUID getFinishedArticleId() { return finishedArticleId; }
    public GtinPackaging getPackaging() { return packaging; }
    public int getUnitsPerPack() { return unitsPerPack; }
    public String getDescription() { return description; }
    public GtinStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getRetiredAt() { return retiredAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Une production d'une mise : un produit fini, ses bouteilles, son vin, ses matières sèches,
 * et son numéro de lot, attribué dès la planification (avant le début de la mise).
 * Elle se clôture seule : la production du matin peut l'être avant celle de l'après-midi.
 */
@Entity
@Table(name = "bottling_productions")
public class BottlingProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bottling_id", updatable = false)
    private Bottling bottling;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductionStatus status = ProductionStatus.PLANNED;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductionSlot slot = ProductionSlot.FULL_DAY;

    // UUID : l'article (produit fini) appartient au module Référentiel
    @Column(nullable = false)
    private UUID finishedArticleId;

    @Column(nullable = false)
    private int plannedBottles;

    @Column(nullable = false)
    private int bottleVolumeCl;

    // UUID : la cuve appartient au module Cuverie (mise chez nous)
    private UUID sourceTankId;

    // Mise chez le producteur : le vin mis en bouteille, en clair
    private String wineDescription;

    // Ce qui compose le numéro de lot (UUID : l'appellation appartient au module Référentiel)
    private UUID appellationId;

    private Integer vintage;

    private String lotNumber;

    // --- Le réel, saisi à la clôture ---
    private Integer producedBottles;
    private Integer brokenBottles;
    private BigDecimal wineUsedHl;
    private Instant closedAt;
    private UUID closedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "production", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<BottlingMaterial> materials = new ArrayList<>();

    @OneToMany(mappedBy = "production", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<BottlingConsumption> consumptions = new ArrayList<>();

    // Les commandes servies par cette production (bouteilles réservées par ligne de commande)
    @OneToMany(mappedBy = "production", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<BottlingAllocation> allocations = new ArrayList<>();

    protected BottlingProduction() {
    }

    BottlingProduction(Bottling bottling) {
        this.bottling = bottling;
        this.createdAt = Instant.now();
    }

    // ========== Planification ==========

    void plan(LocalDate startDate, LocalDate endDate, ProductionSlot slot, UUID finishedArticleId,
              int plannedBottles, int bottleVolumeCl, UUID sourceTankId, String wineDescription,
              UUID appellationId, Integer vintage) {
        requirePlanned();
        this.startDate = startDate;
        this.endDate = endDate;
        // Sur plusieurs jours, la production occupe les journées entières
        this.slot = startDate.equals(endDate) ? slot : ProductionSlot.FULL_DAY;
        this.finishedArticleId = finishedArticleId;
        this.plannedBottles = plannedBottles;
        this.bottleVolumeCl = bottleVolumeCl;
        this.sourceTankId = sourceTankId;
        this.wineDescription = wineDescription;
        this.appellationId = appellationId;
        this.vintage = vintage;
    }

    /** Le numéro de lot est-il à (ré)attribuer ? Jamais attribué, ou l'appellation ou le millésime ont changé. */
    boolean needsLotNumber(String expectedPrefix) {
        return lotNumber == null || !lotNumber.startsWith(expectedPrefix)
                || !lotNumber.substring(expectedPrefix.length()).chars().allMatch(Character::isDigit);
    }

    void assignLotNumber(String lotNumber) {
        requirePlanned();
        this.lotNumber = lotNumber;
    }

    /**
     * Remplace la liste des matières. Les lignes existantes sont mises à jour sur place plutôt que
     * supprimées puis recréées : Hibernate insère avant de supprimer, et la contrainte
     * UNIQUE (production_id, article_id) refuserait la nouvelle ligne.
     */
    void replaceMaterials(Map<UUID, BigDecimal> quantitiesByArticle) {
        requirePlanned();
        materials.removeIf(material -> !quantitiesByArticle.containsKey(material.getArticleId()));
        Map<UUID, BottlingMaterial> existing = new HashMap<>();
        materials.forEach(material -> existing.put(material.getArticleId(), material));
        quantitiesByArticle.forEach((articleId, quantity) -> {
            BottlingMaterial material = existing.get(articleId);
            if (material != null) {
                material.setPlannedQuantity(quantity);
            } else {
                materials.add(new BottlingMaterial(this, articleId, quantity));
            }
        });
    }

    /** Remplace les commandes servies, en mettant à jour sur place celles qui restent. */
    void replaceAllocations(Map<UUID, Integer> bottlesByOrderLine) {
        requirePlanned();
        allocations.removeIf(allocation -> !bottlesByOrderLine.containsKey(allocation.getOrderLineId()));
        Map<UUID, BottlingAllocation> existing = new HashMap<>();
        allocations.forEach(allocation -> existing.put(allocation.getOrderLineId(), allocation));
        bottlesByOrderLine.forEach((lineId, bottles) -> {
            BottlingAllocation allocation = existing.get(lineId);
            if (allocation != null) {
                allocation.setQuantity(bottles);
            } else {
                allocations.add(new BottlingAllocation(this, lineId, bottles));
            }
        });
    }

    // ========== Cycle de vie ==========

    record Consumed(UUID articleId, BigDecimal quantity) {
    }

    void close(int producedBottles, int brokenBottles, BigDecimal wineUsedHl, List<Consumed> consumed, UUID userId) {
        requirePlanned();
        if (lotNumber == null) {
            throw new BusinessRuleException("La production n'a pas encore de numéro de lot : "
                    + "choisissez son appellation pour le lui attribuer");
        }
        this.status = ProductionStatus.CLOSED;
        this.producedBottles = producedBottles;
        this.brokenBottles = brokenBottles;
        this.wineUsedHl = wineUsedHl;
        consumed.forEach(line -> consumptions.add(new BottlingConsumption(this, line.articleId(), line.quantity())));
        this.closedAt = Instant.now();
        this.closedBy = userId;
    }

    void cancel() {
        requirePlanned();
        this.status = ProductionStatus.CANCELLED;
    }

    void requirePlanned() {
        if (status != ProductionStatus.PLANNED) {
            throw new BusinessRuleException("La production " + (lotNumber != null ? lotNumber + " " : "")
                    + "est " + (status == ProductionStatus.CLOSED ? "clôturée" : "annulée")
                    + " : elle ne peut plus être modifiée");
        }
    }

    /** Le vin nécessaire, en hectolitres : 6 000 bouteilles de 75 cl = 45 hl. */
    public BigDecimal wineNeededHl() {
        return BigDecimal.valueOf((long) plannedBottles * bottleVolumeCl).movePointLeft(4).setScale(2, RoundingMode.HALF_UP);
    }

    // ========== Getters ==========

    public UUID getId() { return id; }
    public Bottling getBottling() { return bottling; }
    public ProductionStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public ProductionSlot getSlot() { return slot; }
    public UUID getFinishedArticleId() { return finishedArticleId; }
    public int getPlannedBottles() { return plannedBottles; }
    public int getBottleVolumeCl() { return bottleVolumeCl; }
    public UUID getSourceTankId() { return sourceTankId; }
    public String getWineDescription() { return wineDescription; }
    public UUID getAppellationId() { return appellationId; }
    public Integer getVintage() { return vintage; }
    public String getLotNumber() { return lotNumber; }
    public Integer getProducedBottles() { return producedBottles; }
    public Integer getBrokenBottles() { return brokenBottles; }
    public BigDecimal getWineUsedHl() { return wineUsedHl; }
    public Instant getClosedAt() { return closedAt; }
    public UUID getClosedBy() { return closedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public List<BottlingMaterial> getMaterials() { return Collections.unmodifiableList(materials); }
    public List<BottlingConsumption> getConsumptions() { return Collections.unmodifiableList(consumptions); }
    public List<BottlingAllocation> getAllocations() { return Collections.unmodifiableList(allocations); }
}

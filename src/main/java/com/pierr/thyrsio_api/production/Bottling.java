package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Une mise en bouteille. Un agrégat, comme l'inventaire : la mise est la racine,
 * ses matières et ses consommations ne se modifient qu'à travers elle.
 * Une mise planifiée se modifie librement ; clôturée ou annulée, elle est figée.
 */
@Entity
@Table(name = "bottlings")
public class Bottling {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BottlingStatus status = BottlingStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BottlingLocation location;

    private String contractor;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // UUID : l'article (produit fini) appartient au module Référentiel
    @Column(nullable = false)
    private UUID finishedArticleId;

    @Column(nullable = false)
    private int plannedBottles;

    @Column(nullable = false)
    private int bottleVolumeCl;

    // UUID : la cuve appartient au module Cuverie
    private UUID sourceTankId;

    private String wineDescription;

    private String notes;

    // --- Le réel, saisi à la clôture ---

    private Integer producedBottles;

    private Integer brokenBottles;

    private BigDecimal wineUsedHl;

    private String lotNumber;

    private Instant closedAt;

    private UUID closedBy;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // Deux personnes qui modifient la même mise : la seconde reçoit un 409 au lieu d'écraser la première
    @Version
    private long version;

    @OneToMany(mappedBy = "bottling", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BottlingMaterial> materials = new ArrayList<>();

    @OneToMany(mappedBy = "bottling", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BottlingConsumption> consumptions = new ArrayList<>();

    protected Bottling() {
    }

    Bottling(String code, UUID createdBy) {
        this.code = code;
        this.createdBy = createdBy;
    }

    // ========== Planification ==========

    /** Les champs du planning, à la création comme en modification. */
    void plan(BottlingLocation location, String contractor, LocalDate startDate, LocalDate endDate,
              UUID finishedArticleId, int plannedBottles, int bottleVolumeCl,
              UUID sourceTankId, String wineDescription, String notes) {
        requirePlanned();
        this.location = location;
        this.contractor = contractor;
        this.startDate = startDate;
        this.endDate = endDate;
        this.finishedArticleId = finishedArticleId;
        this.plannedBottles = plannedBottles;
        this.bottleVolumeCl = bottleVolumeCl;
        this.sourceTankId = sourceTankId;
        this.wineDescription = wineDescription;
        this.notes = notes;
        touch();
    }

    /**
     * Remplace la liste des matières. Les lignes existantes sont mises à jour sur place plutôt que
     * supprimées puis recréées : Hibernate insère avant de supprimer, et la contrainte
     * UNIQUE (bottling_id, article_id) refuserait la nouvelle ligne.
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
        touch();
    }

    // ========== Cycle de vie ==========

    record Consumed(UUID articleId, UUID lotId, BigDecimal quantity) {
    }

    void close(int producedBottles, int brokenBottles, BigDecimal wineUsedHl, String lotNumber,
               List<Consumed> consumed, UUID userId) {
        requirePlanned();
        this.status = BottlingStatus.CLOSED;
        this.producedBottles = producedBottles;
        this.brokenBottles = brokenBottles;
        this.wineUsedHl = wineUsedHl;
        this.lotNumber = lotNumber;
        consumed.forEach(line -> consumptions.add(
                new BottlingConsumption(this, line.articleId(), line.lotId(), line.quantity())));
        this.closedAt = Instant.now();
        this.closedBy = userId;
        touch();
    }

    void cancel() {
        requirePlanned();
        this.status = BottlingStatus.CANCELLED;
        touch();
    }

    void requirePlanned() {
        if (status != BottlingStatus.PLANNED) {
            throw new BusinessRuleException("La mise " + code + " n'est plus planifiée (" + status
                    + ") : elle ne peut plus être modifiée");
        }
    }

    /** Le vin nécessaire, en hectolitres : 6 000 bouteilles de 75 cl = 45 hl. */
    public BigDecimal wineNeededHl() {
        return BigDecimal.valueOf((long) plannedBottles * bottleVolumeCl).movePointLeft(4).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ========== Getters ==========

    public UUID getId() { return id; }

    public String getCode() { return code; }

    public BottlingStatus getStatus() { return status; }

    public BottlingLocation getLocation() { return location; }

    public String getContractor() { return contractor; }

    public LocalDate getStartDate() { return startDate; }

    public LocalDate getEndDate() { return endDate; }

    public UUID getFinishedArticleId() { return finishedArticleId; }

    public int getPlannedBottles() { return plannedBottles; }

    public int getBottleVolumeCl() { return bottleVolumeCl; }

    public UUID getSourceTankId() { return sourceTankId; }

    public String getWineDescription() { return wineDescription; }

    public String getNotes() { return notes; }

    public Integer getProducedBottles() { return producedBottles; }

    public Integer getBrokenBottles() { return brokenBottles; }

    public BigDecimal getWineUsedHl() { return wineUsedHl; }

    public String getLotNumber() { return lotNumber; }

    public Instant getClosedAt() { return closedAt; }

    public UUID getClosedBy() { return closedBy; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public long getVersion() { return version; }

    public List<BottlingMaterial> getMaterials() {
        return Collections.unmodifiableList(materials);
    }

    public List<BottlingConsumption> getConsumptions() {
        return Collections.unmodifiableList(consumptions);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
}

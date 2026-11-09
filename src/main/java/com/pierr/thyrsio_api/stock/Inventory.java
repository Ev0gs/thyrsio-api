package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Un agrégat : l'inventaire est la "racine", ses lignes ne se modifient qu'à travers lui.
 * C'est lui qui garantit qu'on ne touche plus à rien une fois validé ou annulé.
 */
@Entity
@Table(name = "inventories")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryStatus status = InventoryStatus.DRAFT;

    @Column(nullable = false)
    private Instant countedAt;

    private String notes;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID validatedBy;

    private Instant validatedAt;

    // Hibernate ajoute "WHERE version = ?" à chaque UPDATE et incrémente la valeur.
    // Si quelqu'un a modifié l'inventaire entre-temps, aucune ligne ne correspond → exception → 409.
    @Version
    private long version;

    // cascade ALL : enregistrer l'inventaire enregistre ses lignes.
    // orphanRemoval : retirer une ligne de la liste la supprime en base.
    @OneToMany(mappedBy = "inventory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryLine> lines = new ArrayList<>();

    protected Inventory() {
    }

    public Inventory(String label, Instant countedAt, String notes, UUID createdBy) {
        this.label = label;
        this.countedAt = countedAt;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    // --- Les lignes : toujours en passant par l'inventaire ---

    InventoryLine addLine(UUID articleId, Lot lot) {
        requireDraft();
        UUID lotId = lot != null ? lot.getId() : null;
        if (lines.stream().anyMatch(line -> line.concerns(articleId, lotId))) {
            throw new BusinessRuleException("Cet article (et ce lot) figure déjà dans l'inventaire");
        }
        InventoryLine line = new InventoryLine(this, articleId, lot);
        lines.add(line);
        touch();
        return line;
    }

    void count(InventoryLine line, BigDecimal countedQuantity, String notes) {
        requireDraft();
        line.setCount(countedQuantity, notes);
        touch();
    }

    void removeLine(InventoryLine line) {
        requireDraft();
        lines.remove(line);
        touch();
    }

    Optional<InventoryLine> findLine(UUID lineId) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst();
    }

    // --- Cycle de vie ---

    void markValidated(UUID userId) {
        requireDraft();
        this.status = InventoryStatus.VALIDATED;
        this.validatedBy = userId;
        this.validatedAt = Instant.now();
        touch();
    }

    void cancel() {
        requireDraft();
        this.status = InventoryStatus.CANCELLED;
        touch();
    }

    void requireDraft() {
        if (status != InventoryStatus.DRAFT) {
            throw new BusinessRuleException("L'inventaire n'est plus en brouillon (" + status + ") : il ne peut plus être modifié");
        }
    }

    /**
     * Modifier une ligne ne modifie pas la ligne "inventories" en base, donc n'incrémenterait pas la version.
     * En changeant updatedAt, toute modification d'une ligne incrémente aussi la version de l'inventaire.
     */
    private void touch() {
        this.updatedAt = Instant.now();
    }

    // --- Getters ---

    public UUID getId() { return id; }

    public String getLabel() { return label; }

    public InventoryStatus getStatus() { return status; }

    public Instant getCountedAt() { return countedAt; }

    public String getNotes() { return notes; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public UUID getValidatedBy() { return validatedBy; }

    public Instant getValidatedAt() { return validatedAt; }

    /** En lecture seule : impossible d'ajouter une ligne sans passer par addLine. */
    public List<InventoryLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
}
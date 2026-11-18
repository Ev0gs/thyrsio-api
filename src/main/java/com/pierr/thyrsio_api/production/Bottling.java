package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Une mise en bouteille : un événement, en un lieu, sur une demi-journée, une journée ou plusieurs,
 * qui réalise une ou plusieurs productions (un produit fini le matin, un autre l'après-midi...).
 * Un agrégat : la mise est la racine, ses productions ne se modifient qu'à travers elle.
 * Ses dates et son état se déduisent de ses productions.
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

    // Le premier et le dernier jour de ses productions
    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private String notes;

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
    @OrderBy("startDate, createdAt")
    @BatchSize(size = 50)
    private List<BottlingProduction> productions = new ArrayList<>();

    protected Bottling() {
    }

    Bottling(String code, UUID createdBy) {
        this.code = code;
        this.createdBy = createdBy;
    }

    // ========== Planification ==========

    void plan(BottlingLocation location, String contractor, String notes) {
        requireOpen();
        if (this.location != null && this.location != location
                && productions.stream().anyMatch(p -> p.getStatus() == ProductionStatus.CLOSED)) {
            throw new BusinessRuleException("La mise " + code + " a déjà une production clôturée : "
                    + "son lieu ne peut plus changer");
        }
        this.location = location;
        this.contractor = contractor;
        this.notes = notes;
        touch();
    }

    BottlingProduction addProduction() {
        requireOpen();
        BottlingProduction production = new BottlingProduction(this);
        productions.add(production);
        return production;
    }

    /** Retire une production planifiée : son numéro de lot n'est pas rendu, il ne servira plus. */
    void removeProduction(BottlingProduction production) {
        production.requirePlanned();
        productions.remove(production);
    }

    // ========== Cycle de vie ==========

    /** Annule ce qui reste à faire ; les productions déjà clôturées restent. */
    void cancel() {
        requireOpen();
        productions.stream()
                .filter(p -> p.getStatus() == ProductionStatus.PLANNED)
                .forEach(BottlingProduction::cancel);
        refresh();
    }

    /** Recalcule les dates et l'état à partir des productions. */
    void refresh() {
        if (productions.isEmpty()) {
            throw new IllegalArgumentException("Une mise réalise au moins une production");
        }
        List<BottlingProduction> counted = productions.stream()
                .filter(p -> p.getStatus() != ProductionStatus.CANCELLED)
                .toList();
        List<BottlingProduction> span = counted.isEmpty() ? productions : counted;
        this.startDate = span.stream().map(BottlingProduction::getStartDate).min(Comparator.naturalOrder()).orElseThrow();
        this.endDate = span.stream().map(BottlingProduction::getEndDate).max(Comparator.naturalOrder()).orElseThrow();

        boolean anyPlanned = counted.stream().anyMatch(p -> p.getStatus() == ProductionStatus.PLANNED);
        boolean anyClosed = counted.stream().anyMatch(p -> p.getStatus() == ProductionStatus.CLOSED);
        if (counted.isEmpty()) {
            this.status = BottlingStatus.CANCELLED;
        } else if (anyPlanned) {
            this.status = anyClosed ? BottlingStatus.IN_PROGRESS : BottlingStatus.PLANNED;
        } else {
            this.status = BottlingStatus.CLOSED;
        }
        touch();
    }

    /** Planifiée ou en cours : il reste des productions à faire. */
    void requireOpen() {
        if (status == BottlingStatus.CLOSED || status == BottlingStatus.CANCELLED) {
            throw new BusinessRuleException("La mise " + code + " est "
                    + (status == BottlingStatus.CLOSED ? "clôturée" : "annulée") + " : elle ne peut plus être modifiée");
        }
    }

    BottlingProduction production(UUID productionId) {
        return productions.stream()
                .filter(p -> p.getId() != null && p.getId().equals(productionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "La production " + productionId + " n'appartient pas à la mise " + code));
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
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public List<BottlingProduction> getProductions() {
        return Collections.unmodifiableList(productions);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
}

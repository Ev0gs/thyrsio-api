package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Une cuve et son contenu actuel. Elle porte elle-même son "solde" (lot + volume) :
 * avec un seul vin à la fois, pas besoin d'une table de soldes séparée comme pour le stock.
 * Toutes les règles qui touchent au contenu sont ici, et les modifications sont package-private :
 * seul le module Cuverie fait bouger une cuve, toujours avec un mouvement.
 */
@Entity
@Table(name = "tanks")
public class Tank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    private String label;

    @Column(nullable = false)
    private BigDecimal capacityHl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TankStatus status = TankStatus.IN_SERVICE;

    // null quand la cuve est vide (contrainte tanks_lot_iff_volume)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wine_lot_id")
    private WineLot wineLot;

    @Column(nullable = false)
    private BigDecimal volumeHl = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Tank() {
    }

    public Tank(String code, String label, BigDecimal capacityHl) {
        this.code = code;
        this.label = label;
        this.capacityHl = capacityHl;
        this.updatedAt = Instant.now();
    }

    // ========== Contenu ==========

    /**
     * Ajoute (delta > 0) ou retire (delta < 0) du vin du lot donné.
     * Le service a verrouillé la cuve avant : personne d'autre ne la modifie en même temps.
     */
    void apply(WineLot lot, BigDecimal delta) {
        BigDecimal next = volumeHl.add(delta);
        if (delta.signum() > 0) {
            if (!active) {
                throw new BusinessRuleException("La cuve " + code + " est archivée : elle ne peut plus recevoir de vin");
            }
            if (status != TankStatus.IN_SERVICE) {
                throw new BusinessRuleException("La cuve " + code + " est " + status.getLabel()
                        + " : elle ne peut pas recevoir de vin");
            }
            if (!isEmpty() && !contains(lot)) {
                throw new BusinessRuleException("La cuve " + code + " contient déjà le lot " + wineLot.describe()
                        + " : une cuve ne contient qu'un seul vin à la fois");
            }
            if (next.compareTo(capacityHl) > 0) {
                throw new BusinessRuleException("Capacité dépassée pour la cuve " + code + " : "
                        + plain(freeSpaceHl()) + " hl de place, " + plain(delta) + " hl demandés");
            }
        } else {
            if (isEmpty() || !contains(lot)) {
                throw new IllegalStateException("Le lot " + lot.getCode() + " n'est pas dans la cuve " + code);
            }
            if (next.signum() < 0) {
                throw new BusinessRuleException("Volume insuffisant dans la cuve " + code + " : "
                        + plain(volumeHl) + " hl présents, " + plain(delta.negate()) + " hl demandés");
            }
        }
        this.volumeHl = next;
        // Vidée : la cuve se libère d'elle-même
        this.wineLot = next.signum() == 0 ? null : lot;
        touch();
    }

    // ========== Fiche ==========

    void setLabel(String label) {
        this.label = label;
        touch();
    }

    void changeCapacity(BigDecimal capacityHl) {
        if (capacityHl.compareTo(volumeHl) < 0) {
            throw new BusinessRuleException("La cuve " + code + " contient " + plain(volumeHl)
                    + " hl : sa contenance ne peut pas être inférieure");
        }
        this.capacityHl = capacityHl;
        touch();
    }

    void changeStatus(TankStatus status) {
        if (status != TankStatus.IN_SERVICE && !isEmpty()) {
            throw new BusinessRuleException("La cuve " + code + " contient du vin : videz-la avant de la passer "
                    + status.getLabel());
        }
        this.status = status;
        touch();
    }

    void archive() {
        if (!isEmpty()) {
            throw new BusinessRuleException("La cuve " + code + " contient du vin : videz-la avant de l'archiver");
        }
        this.active = false;
        touch();
    }

    void reactivate() {
        this.active = true;
        touch();
    }

    // ========== Lecture ==========

    public boolean isEmpty() {
        return volumeHl.signum() == 0;
    }

    public boolean contains(WineLot lot) {
        return wineLot != null && lot != null && wineLot.getId().equals(lot.getId());
    }

    public BigDecimal freeSpaceHl() {
        return capacityHl.subtract(volumeHl);
    }

    /** Ce que l'écran affiche : le statut manuel d'abord, sinon vide ou occupée selon le volume. */
    public TankState getState() {
        return switch (status) {
            case CLEANING -> TankState.CLEANING;
            case OUT_OF_SERVICE -> TankState.OUT_OF_SERVICE;
            case IN_SERVICE -> isEmpty() ? TankState.EMPTY : TankState.IN_USE;
        };
    }

    public UUID getId() { return id; }

    public String getCode() { return code; }

    public String getLabel() { return label; }

    public BigDecimal getCapacityHl() { return capacityHl; }

    public TankStatus getStatus() { return status; }

    public WineLot getWineLot() { return wineLot; }

    public BigDecimal getVolumeHl() { return volumeHl; }

    public boolean isActive() { return active; }

    public Instant getUpdatedAt() { return updatedAt; }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    /** 120.50 → "120.5" pour les messages d'erreur. */
    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

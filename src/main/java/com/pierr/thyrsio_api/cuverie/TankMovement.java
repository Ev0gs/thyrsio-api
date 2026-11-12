package com.pierr.thyrsio_api.cuverie;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable // Comme pour le stock : un mouvement ne se modifie pas, il se compense
@Table(name = "tank_movements")
public class TankMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tank_id")
    private Tank tank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wine_lot_id")
    private WineLot wineLot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TankMovementType type;

    // Signé : positif pour une entrée dans la cuve, négatif pour une sortie
    @Column(nullable = false)
    private BigDecimal volumeHl;

    private UUID transferId;

    @Column(nullable = false)
    private Instant occurredAt;

    private String reference;

    private String notes;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected TankMovement() {
    }

    TankMovement(Tank tank, WineLot wineLot, TankMovementType type, BigDecimal signedVolumeHl, UUID transferId,
                 Instant occurredAt, String reference, String notes, UUID createdBy) {
        type.checkSign(signedVolumeHl);
        this.tank = tank;
        this.wineLot = wineLot;
        this.type = type;
        this.volumeHl = signedVolumeHl;
        this.transferId = transferId;
        this.occurredAt = occurredAt;
        this.reference = reference;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }

    public Tank getTank() { return tank; }

    public WineLot getWineLot() { return wineLot; }

    public TankMovementType getType() { return type; }

    public BigDecimal getVolumeHl() { return volumeHl; }

    public UUID getTransferId() { return transferId; }

    public Instant getOccurredAt() { return occurredAt; }

    public String getReference() { return reference; }

    public String getNotes() { return notes; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

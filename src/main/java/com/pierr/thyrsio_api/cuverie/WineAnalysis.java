package com.pierr.thyrsio_api.cuverie;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Une analyse de laboratoire. Chaque valeur est facultative : on note ce qui a été mesuré. */
@Entity
@Table(name = "wine_analyses")
public class WineAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wine_lot_id", updatable = false)
    private WineLot wineLot;

    // La cuve prélevée, si on la connaît
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tank_id", updatable = false)
    private Tank tank;

    @Column(nullable = false)
    private LocalDate analyzedOn;

    private BigDecimal alcohol;          // % vol.

    @Column(name = "free_so2")
    private BigDecimal freeSo2;          // mg/L

    @Column(name = "total_so2")
    private BigDecimal totalSo2;         // mg/L

    private BigDecimal totalAcidity;     // g/L H2SO4

    private BigDecimal volatileAcidity;  // g/L H2SO4

    private String laboratory;

    private String notes;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected WineAnalysis() {
    }

    WineAnalysis(WineLot wineLot, Tank tank, LocalDate analyzedOn, BigDecimal alcohol, BigDecimal freeSo2,
                 BigDecimal totalSo2, BigDecimal totalAcidity, BigDecimal volatileAcidity,
                 String laboratory, String notes, UUID createdBy) {
        this.wineLot = wineLot;
        this.tank = tank;
        this.analyzedOn = analyzedOn;
        this.alcohol = alcohol;
        this.freeSo2 = freeSo2;
        this.totalSo2 = totalSo2;
        this.totalAcidity = totalAcidity;
        this.volatileAcidity = volatileAcidity;
        this.laboratory = laboratory;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }

    public WineLot getWineLot() { return wineLot; }

    public Tank getTank() { return tank; }

    public LocalDate getAnalyzedOn() { return analyzedOn; }

    public BigDecimal getAlcohol() { return alcohol; }

    public BigDecimal getFreeSo2() { return freeSo2; }

    public BigDecimal getTotalSo2() { return totalSo2; }

    public BigDecimal getTotalAcidity() { return totalAcidity; }

    public BigDecimal getVolatileAcidity() { return volatileAcidity; }

    public String getLaboratory() { return laboratory; }

    public String getNotes() { return notes; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

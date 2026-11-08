package com.pierr.thyrsio_api.stock;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lots")
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Un UUID, pas une relation JPA : l'article appartient au module Référentiel
    @Column(nullable = false, updatable = false)
    private UUID articleId;

    @Column(nullable = false, updatable = false)
    private String lotNumber;

    // LocalDate : une date sans heure ni fuseau, comme une date de péremption
    private LocalDate expiryDate;

    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Lot() {
    }

    public Lot(UUID articleId, String lotNumber) {
        this.articleId = articleId;
        this.lotNumber = lotNumber;
    }

    public UUID getId() { return id; }

    public UUID getArticleId() { return articleId; }

    public String getLotNumber() { return lotNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }

    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getNotes() { return notes; }

    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
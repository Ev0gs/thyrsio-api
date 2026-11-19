package com.pierr.thyrsio_api.gs1;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Un préfixe entreprise attribué par GS1 (376012345 : 1 000 gencodes). */
@Entity
@Table(name = "gs1_prefixes")
public class Gs1Prefix {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Définitif : les gencodes déjà créés en dépendent
    @Column(nullable = false, updatable = false, unique = true)
    private String prefix;

    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Gs1Prefix() {
    }

    Gs1Prefix(String prefix, String label) {
        this.prefix = prefix;
        this.label = label;
    }

    public UUID getId() { return id; }
    public String getPrefix() { return prefix; }
    public String getLabel() { return label; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    void update(String label, boolean active) {
        this.label = label;
        this.active = active;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

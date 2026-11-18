package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Une appellation (Bordeaux, Côtes de Bourg...) et son sigle (BX), qui entre dans le numéro de lot
 * des bouteilles : L + sigle + millésime + numéro d'ordre (LBX2401).
 */
@Entity
@Table(name = "appellations")
public class Appellation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Des lettres seulement (BX, CDB...) : le millésime qui suit ne peut pas s'y confondre
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Appellation() {
    }

    public Appellation(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    void rename(String code, String name) {
        this.code = code;
        this.name = name;
    }

    void archive() { this.active = false; }

    void restore() { this.active = true; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

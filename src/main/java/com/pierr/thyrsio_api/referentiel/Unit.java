package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "units")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identifiant lisible (U, L, HL...) : ne change jamais une fois créé
    @Column(nullable = false, updatable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    // Pas de setter : changer les décimales d'une unité déjà utilisée rendrait les stocks incohérents.
    @Column(nullable = false, updatable = false)
    private short decimals;

    protected Unit() {
    }

    public Unit(String code, String label, short decimals) {
        this.code = code;
        this.label = label;
        this.decimals = decimals;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public short getDecimals() {
        return decimals;
    }
}

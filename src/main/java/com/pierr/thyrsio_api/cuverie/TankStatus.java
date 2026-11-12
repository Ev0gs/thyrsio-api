package com.pierr.thyrsio_api.cuverie;

/**
 * L'état "humain" d'une cuve, choisi par l'utilisateur.
 * Vide ou occupée ne se saisit pas : ça se déduit du volume (voir TankState).
 */
public enum TankStatus {

    IN_SERVICE("en service"),
    CLEANING("en nettoyage"),
    OUT_OF_SERVICE("hors service");

    private final String label;

    TankStatus(String label) {
        this.label = label;
    }

    /** Pour les messages d'erreur : "La cuve C03 est en nettoyage". */
    public String getLabel() {
        return label;
    }
}

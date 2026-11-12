package com.pierr.thyrsio_api.cuverie;

/** Ce que l'écran affiche : calculé à partir du statut et du volume, jamais stocké. */
public enum TankState {
    EMPTY,
    IN_USE,
    CLEANING,
    OUT_OF_SERVICE
}

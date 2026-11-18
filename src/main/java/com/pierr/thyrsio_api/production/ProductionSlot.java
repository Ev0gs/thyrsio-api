package com.pierr.thyrsio_api.production;

import java.time.LocalTime;

/**
 * Le moment d'une production sur un jour : le matin, l'après-midi, ou toute la journée.
 * Une production sur plusieurs jours est toujours FULL_DAY.
 * end : l'heure des mouvements de stock à la clôture ; rank : l'ordre dans la journée.
 */
public enum ProductionSlot {
    MORNING(LocalTime.of(12, 30), 0),
    AFTERNOON(LocalTime.of(17, 30), 1),
    FULL_DAY(LocalTime.of(17, 30), 0);

    private final LocalTime end;
    private final int rank;

    ProductionSlot(LocalTime end, int rank) {
        this.end = end;
        this.rank = rank;
    }

    public LocalTime end() { return end; }

    public int rank() { return rank; }
}

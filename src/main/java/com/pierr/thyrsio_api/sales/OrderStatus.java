package com.pierr.thyrsio_api.sales;

public enum OrderStatus {
    DRAFT,              // en cours de saisie : ne réserve rien
    CONFIRMED,          // confirmée : compte dans la couverture (stock, mises) et peut être expédiée
    PARTIALLY_SHIPPED,  // une partie est partie
    SHIPPED,            // tout est parti
    CANCELLED;

    /** Une commande "ouverte" attend encore des bouteilles. */
    public boolean isOpen() {
        return this == CONFIRMED || this == PARTIALLY_SHIPPED;
    }
}

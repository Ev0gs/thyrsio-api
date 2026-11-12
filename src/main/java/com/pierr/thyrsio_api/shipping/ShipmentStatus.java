package com.pierr.thyrsio_api.shipping;

public enum ShipmentStatus {
    DRAFT,     // en préparation : rien n'est sorti du stock
    SHIPPED,   // parti : le stock est sorti, la commande a avancé, le bon de livraison est figé
    CANCELLED
}

package com.pierr.thyrsio_api.production;

/**
 * Où a lieu la mise. Chez nous, le vin sort d'une de nos cuves (mouvement BOTTLING à la clôture).
 * Chez le producteur, le prestataire embouteille sur place : le vin ne passe pas par nos cuves.
 */
public enum BottlingLocation {
    ON_SITE,
    AT_PRODUCER
}

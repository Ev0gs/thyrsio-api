package com.pierr.thyrsio_api.referentiel;

/**
 * Un point sur la carte. approximate : seule la ville ou le code postal a été trouvé, pas la rue.
 * Absent (null) dans les réponses tant que l'adresse n'a pas été localisée, ou si elle est introuvable.
 */
public record GeoLocation(double latitude, double longitude, boolean approximate) {
}

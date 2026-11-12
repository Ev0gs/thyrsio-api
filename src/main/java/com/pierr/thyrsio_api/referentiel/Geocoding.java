package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.Embeddable;

import java.time.Instant;
import java.util.Objects;

/**
 * La position d'une adresse sur la carte, et la recherche qui l'a donnée.
 * Partagé par les clients, leurs adresses de livraison et les fournisseurs.
 * Hibernate charge un Geocoding dont toutes les colonnes sont nulles comme null : d'où les méthodes statiques.
 */
@Embeddable
public class Geocoding {

    private Double latitude;
    private Double longitude;
    private Boolean locationApproximate;
    private String geocodingQuery;
    private Instant geocodedAt;

    protected Geocoding() {
    }

    /**
     * Appelé avant chaque enregistrement : si l'adresse a changé, l'ancienne position est effacée
     * et l'adresse repart dans la file du géocodage.
     */
    static Geocoding track(Geocoding current, GeocodingRequest request) {
        Geocoding geocoding = current != null ? current : new Geocoding();
        String query = request != null ? request.query() : null;
        if (!Objects.equals(query, geocoding.geocodingQuery)) {
            geocoding.geocodingQuery = query;
            geocoding.latitude = null;
            geocoding.longitude = null;
            geocoding.locationApproximate = null;
            geocoding.geocodedAt = null;
        }
        return geocoding;
    }

    /**
     * La position, si elle correspond bien à l'adresse actuelle : une adresse modifiée mais pas encore
     * enregistrée en base (réponse d'un PUT) n'affiche pas l'ancienne position.
     */
    static GeoLocation location(Geocoding geocoding, GeocodingRequest current) {
        String query = current != null ? current.query() : null;
        if (geocoding == null || geocoding.latitude == null || geocoding.longitude == null
                || !Objects.equals(query, geocoding.geocodingQuery)) {
            return null;
        }
        return new GeoLocation(geocoding.latitude, geocoding.longitude,
                Boolean.TRUE.equals(geocoding.locationApproximate));
    }
}

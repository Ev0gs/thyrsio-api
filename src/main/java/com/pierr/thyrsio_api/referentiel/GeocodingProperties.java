package com.pierr.thyrsio_api.referentiel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * thyrsio.geocoding.* : la localisation des adresses sur la carte.
 * Désactivée par défaut (les tests n'appellent pas Internet) ; activée par le profil dev.
 *
 * @param userAgent Nominatim exige qu'il identifie l'application ; mettre une adresse de contact en production
 * @param interval  le délai entre deux passages de la tâche de fond
 */
@ConfigurationProperties("thyrsio.geocoding")
public record GeocodingProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://nominatim.openstreetmap.org") String baseUrl,
        @DefaultValue("Thyrsio ERP (negoce de vins)") String userAgent,
        @DefaultValue("PT1M") Duration interval
) {
}

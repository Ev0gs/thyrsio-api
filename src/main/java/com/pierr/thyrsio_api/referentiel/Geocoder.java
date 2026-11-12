package com.pierr.thyrsio_api.referentiel;

import java.util.Optional;

/** Trouve une adresse sur la carte. */
public interface Geocoder {

    /**
     * Vide : l'adresse est introuvable (on ne la cherchera plus tant qu'elle ne change pas).
     *
     * @throws GeocodingUnavailableException le service ne répond pas : on réessaiera plus tard
     */
    Optional<GeoLocation> locate(GeocodingRequest request);

    class GeocodingUnavailableException extends RuntimeException {
        public GeocodingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

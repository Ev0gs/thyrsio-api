package com.pierr.thyrsio_api.referentiel;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ce qu'on cherche sur la carte : une adresse structurée (clients, adresses de livraison)
 * ou un texte libre (fournisseurs, dont l'adresse tient en un champ).
 */
public record GeocodingRequest(String street, String postalCode, String city, String countryCode, String freeText) {

    /** null : rien à chercher (pas de ville, ou pas d'adresse du tout). */
    static GeocodingRequest structured(String street, String postalCode, String city, String countryCode) {
        if (isBlank(city)) {
            return null;
        }
        return new GeocodingRequest(trim(street), trim(postalCode), city.trim(),
                countryCode != null ? countryCode.trim().toUpperCase(Locale.ROOT) : null, null);
    }

    static GeocodingRequest freeText(String text) {
        if (isBlank(text)) {
            return null;
        }
        // Une adresse saisie sur plusieurs lignes se cherche comme une seule
        String oneLine = text.trim().replaceAll("\\s*\\R\\s*", ", ");
        return new GeocodingRequest(null, null, null, null, oneLine);
    }

    /**
     * La forme canonique de la recherche, enregistrée avec le résultat :
     * si elle change, l'adresse a changé et il faut la relocaliser.
     */
    public String query() {
        if (freeText != null) {
            return freeText;
        }
        return Stream.of(street, postalCode, city, countryCode)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" | "));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return isBlank(value) ? null : value.trim();
    }
}

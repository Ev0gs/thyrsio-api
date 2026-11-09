package com.pierr.thyrsio_api.referentiel;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Vérification des codes ISO de pays et de langue, partagée dans le module. */
final class IsoCodes {

    // copyOf tolère les doublons, contrairement à Set.of
    private static final Set<String> COUNTRIES = Set.copyOf(Arrays.asList(Locale.getISOCountries()));
    private static final Set<String> LANGUAGES = Set.copyOf(Arrays.asList(Locale.getISOLanguages()));

    private IsoCodes() {
    }

    /** "jp " devient "JP" ; un code inconnu est refusé (400). */
    static String country(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!COUNTRIES.contains(code)) {
            throw new IllegalArgumentException("Code pays inconnu : " + code);
        }
        return code;
    }

    /** "EN" devient "en" ; un code inconnu est refusé (400). */
    static String language(String value) {
        String code = value.trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(code)) {
            throw new IllegalArgumentException("Code langue inconnu : " + code);
        }
        return code;
    }
}
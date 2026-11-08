package com.pierr.thyrsio_api.common;

import java.util.Locale;

/** Règles communes aux codes lisibles (U, BOUTEILLE, CHAI-1...). */
public final class Codes {

    private Codes() {
    }

    /** "  chai-1 " devient "CHAI-1" : un code n'existe qu'en une seule version. */
    public static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}

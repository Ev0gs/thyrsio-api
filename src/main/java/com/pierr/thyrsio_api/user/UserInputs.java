package com.pierr.thyrsio_api.user;

import java.util.Locale;

public final class UserInputs {

    private UserInputs() {}

    public static String normalizeEmail(String email)
    {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeName(String name)
    {
        String trimmed = name.trim();
        if(trimmed.isEmpty()) {
            throw new IllegalArgumentException("Le nom ne peut pas être vide");
        }
        return trimmed;
    }
}

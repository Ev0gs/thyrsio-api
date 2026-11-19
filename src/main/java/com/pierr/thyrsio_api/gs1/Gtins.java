package com.pierr.thyrsio_api.gs1;

/**
 * Le calcul GS1 des gencodes EAN-13 : 12 chiffres (préfixe entreprise + référence article) et une clé.
 * La clé : en partant de la droite, les chiffres sont pondérés 3, 1, 3, 1... ; la clé complète la somme
 * à la dizaine supérieure. 376012345678 → somme 106 → clé 4 → 3760123456784.
 */
public final class Gtins {

    private Gtins() {
    }

    /** La clé de contrôle de ces chiffres (sans la clé). */
    public static int checkDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - 1 - i) - '0';
            sum += digit * (i % 2 == 0 ? 3 : 1);
        }
        return (10 - sum % 10) % 10;
    }

    /** 12 chiffres → l'EAN-13 complet. */
    public static String withCheckDigit(String twelveDigits) {
        return twelveDigits + checkDigit(twelveDigits);
    }

    /** Un EAN-13 valide : 13 chiffres dont le dernier est la bonne clé. */
    public static boolean isValid(String gtin) {
        return gtin != null && gtin.matches("\\d{13}")
                && checkDigit(gtin.substring(0, 12)) == gtin.charAt(12) - '0';
    }

    /** "3 760123 456784" ou "3760123-456784" → "3760123456784" (les séparateurs de saisie sont retirés). */
    public static String normalize(String input) {
        return input == null ? null : input.replaceAll("[\\s.-]", "");
    }

    /** Combien de gencodes un préfixe permet : 12 chiffres moins ceux du préfixe. */
    public static long capacity(String prefix) {
        return (long) Math.pow(10, 12 - prefix.length());
    }
}

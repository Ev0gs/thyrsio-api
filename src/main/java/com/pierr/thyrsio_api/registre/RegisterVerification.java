package com.pierr.thyrsio_api.registre;

/**
 * Le résultat de la vérification du registre.
 *
 * @param verifiedEntries le nombre d'écritures intactes, depuis la première
 * @param brokenAt        la première écriture en défaut (null si tout est intact)
 * @param lastHash        l'empreinte de la dernière écriture : la reporter sur une édition papier
 *                        permet de prouver plus tard que rien n'a changé depuis
 */
public record RegisterVerification(boolean valid, long verifiedEntries, Long brokenAt, String problem, String lastHash) {

    static RegisterVerification valid(long entries, String lastHash) {
        return new RegisterVerification(true, entries, null, null, lastHash);
    }

    static RegisterVerification broken(long verified, long brokenAt, String problem) {
        return new RegisterVerification(false, verified, brokenAt, problem, null);
    }
}

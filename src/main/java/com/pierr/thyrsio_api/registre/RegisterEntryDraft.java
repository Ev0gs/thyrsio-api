package com.pierr.thyrsio_api.registre;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qu'un module demande d'inscrire au registre.
 *
 * @param kind             le type d'opération (RECEPTION, TRANSFER, LOSS, MEASURE, BOTTLING_DRAW, BOTTLING, CORRECTION...)
 * @param summary          une phrase lisible : « Réception de 75 hl en C1 (DAE 24FR...) »
 * @param data             les colonnes propres au registre (clé → valeur simple, liste ou objet)
 * @param deadlineDays     le délai d'inscription en jours ouvrables (1, ou 3 pour une sortie) ; 0 : pas de délai
 * @param correctsEntryId  pour une contre-écriture : l'écriture corrigée
 */
public record RegisterEntryDraft(
        RegisterBook book,
        String kind,
        Instant operationAt,
        UUID recordedBy,
        UUID wineLotId,
        String wineLotCode,
        String denomination,
        String tanks,
        BigDecimal volumeHl,
        String reference,
        String summary,
        Map<String, Object> data,
        int deadlineDays,
        UUID correctsEntryId
) {
}

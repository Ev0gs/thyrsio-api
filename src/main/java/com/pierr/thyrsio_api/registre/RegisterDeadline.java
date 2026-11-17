package com.pierr.thyrsio_api.registre;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Les délais d'inscription au registre (règlement d'exécution (UE) 2018/274) :
 * une entrée ou une manipulation au plus tard le jour ouvrable suivant, une sortie au plus tard
 * le troisième jour ouvrable suivant.
 * Les jours ouvrables sont ici du lundi au vendredi ; les jours fériés ne sont pas décomptés,
 * ce qui ne peut que signaler un retard un jour trop tôt, jamais en laisser passer un.
 */
public final class RegisterDeadline {

    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private RegisterDeadline() {
    }

    /** Le dernier jour où l'opération pouvait être inscrite, inclus. */
    public static LocalDate lastDay(Instant operationAt, int workingDays) {
        LocalDate day = operationAt.atZone(ZONE).toLocalDate();
        int added = 0;
        while (added < workingDays) {
            day = day.plusDays(1);
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return day;
    }

    /** Inscrite après la fin du dernier jour permis ? */
    public static boolean isLate(Instant operationAt, Instant recordedAt, int workingDays) {
        return recordedAt.atZone(ZONE).toLocalDate().isAfter(lastDay(operationAt, workingDays));
    }
}

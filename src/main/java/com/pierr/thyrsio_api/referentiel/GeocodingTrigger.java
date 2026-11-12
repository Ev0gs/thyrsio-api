package com.pierr.thyrsio_api.referentiel;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Prévient la tâche de géocodage qu'une adresse vient d'être créée ou modifiée,
 * pour qu'elle apparaisse sur la carte en quelques secondes plutôt qu'au prochain passage.
 * Sans effet quand le géocodage est désactivé (tests).
 */
@Component
class GeocodingTrigger {

    private final ObjectProvider<GeocodingJob> job;

    GeocodingTrigger(ObjectProvider<GeocodingJob> job) {
        this.job = job;
    }

    void addressChanged() {
        job.ifAvailable(GeocodingJob::requestRun);
    }
}

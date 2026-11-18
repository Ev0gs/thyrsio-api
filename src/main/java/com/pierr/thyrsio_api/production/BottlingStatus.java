package com.pierr.thyrsio_api.production;

/**
 * L'état d'une mise, déduit de celui de ses productions :
 * planifiée tant qu'aucune n'est clôturée, en cours quand une partie l'est, clôturée quand il n'en reste
 * aucune à faire, annulée quand toutes le sont.
 */
public enum BottlingStatus {
    PLANNED,
    IN_PROGRESS,
    CLOSED,
    CANCELLED
}

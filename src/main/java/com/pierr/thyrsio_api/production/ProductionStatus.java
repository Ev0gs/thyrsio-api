package com.pierr.thyrsio_api.production;

/** L'état d'une production : planifiée, puis clôturée avec le réel, ou annulée. */
public enum ProductionStatus {
    PLANNED,
    CLOSED,
    CANCELLED
}

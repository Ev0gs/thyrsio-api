package com.pierr.thyrsio_api.referentiel;

/** Où en est la localisation d'une adresse sur la carte. */
public enum LocationStatus {
    /** Trouvée : la position est dans location. */
    LOCATED,
    /** Pas encore cherchée (nouvelle ou modifiée) : la tâche de fond s'en occupe. */
    PENDING,
    /** Cherchée, mais introuvable : à vérifier (ville, code postal). */
    NOT_FOUND,
    /** Pas assez d'adresse pour chercher (pas de ville). */
    NO_ADDRESS
}

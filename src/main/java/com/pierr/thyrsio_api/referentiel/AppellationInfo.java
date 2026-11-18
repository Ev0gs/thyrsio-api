package com.pierr.thyrsio_api.referentiel;

import java.util.UUID;

/** Ce que les autres modules savent d'une appellation : de quoi composer un numéro de lot. */
public record AppellationInfo(UUID id, String code, String name, boolean active) {
}

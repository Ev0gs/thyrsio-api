package com.pierr.thyrsio_api.common;

import java.util.UUID;

/** Une ressource demandée n'existe pas. Traduite en 404 par le GlobalExceptionHandler. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " introuvable : " + id);
    }
}

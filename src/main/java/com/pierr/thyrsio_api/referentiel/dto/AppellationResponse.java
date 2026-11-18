package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.Appellation;

import java.util.UUID;

public record AppellationResponse(UUID id, String code, String name, boolean active) {

    public static AppellationResponse from(Appellation appellation) {
        return new AppellationResponse(appellation.getId(), appellation.getCode(), appellation.getName(),
                appellation.isActive());
    }
}

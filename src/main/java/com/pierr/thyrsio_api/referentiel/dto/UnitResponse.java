package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.Unit;

import java.util.UUID;

public record UnitResponse(
        UUID id,
        String code,
        String label,
        short decimals
){
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getCode(),
                unit.getLabel(),
                unit.getDecimals()
        );
    }
}

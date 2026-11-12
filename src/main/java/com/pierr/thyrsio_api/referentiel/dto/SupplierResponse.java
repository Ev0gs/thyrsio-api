package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.GeoLocation;
import com.pierr.thyrsio_api.referentiel.LocationStatus;
import com.pierr.thyrsio_api.referentiel.Supplier;
import com.pierr.thyrsio_api.referentiel.SupplierKind;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        SupplierKind kind,
        String email,
        String phone,
        String address,
        Integer averageLeadTimeDays,
        boolean active,
        Instant createdAt,
        GeoLocation location,
        LocationStatus locationStatus
) {
    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getKind(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getAverageLeadTimeDays(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getLocation(),
                supplier.getLocationStatus()
        );
    }
}

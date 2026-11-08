package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.Supplier;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String address,
        Integer averageLeadTimeDays,
        boolean active,
        Instant createdAt
) {
    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getAverageLeadTimeDays(),
                supplier.isActive(),
                supplier.getCreatedAt()
        );
    }
}

package com.pierr.thyrsio_api.production.dto;

import com.pierr.thyrsio_api.production.BottlingLocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Planifier ou modifier une mise : son lieu et ses productions.
 * En modification, une production avec un id met à jour celle qui existe (planifiée) ; sans id, elle s'ajoute ;
 * une production planifiée absente de la liste est retirée. Les productions clôturées ou annulées ne changent pas.
 */
public record BottlingRequest(
        @NotNull BottlingLocation location,
        @Size(max = 200) String contractor,
        @Size(max = 2000) String notes,
        @NotNull @Size(max = 20) List<@Valid @NotNull ProductionRequest> productions
) {
}

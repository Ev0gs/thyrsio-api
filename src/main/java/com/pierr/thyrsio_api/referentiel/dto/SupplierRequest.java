package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.SupplierKind;
import jakarta.validation.constraints.*;

public record SupplierRequest(
        @NotBlank @Size(max = 200) String name,
        // Facultatif : DRY_GOODS à la création ; inchangé en modification s'il est absent
        SupplierKind kind,
        @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @Size(max = 500) String address,
        @PositiveOrZero @Max(365) Integer averageLeadTimeDays
) {
}

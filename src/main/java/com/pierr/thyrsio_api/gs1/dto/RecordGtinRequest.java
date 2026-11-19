package com.pierr.thyrsio_api.gs1.dto;

import com.pierr.thyrsio_api.gs1.GtinPackaging;
import jakarta.validation.constraints.*;

import java.util.UUID;

/** Enregistrer un gencode qui existe déjà (créé avant Thyrsio) ; finishedArticleId null : réservé. */
public record RecordGtinRequest(
        @NotBlank @Size(max = 30) String gtin,
        UUID finishedArticleId,
        @NotNull GtinPackaging packaging,
        @Positive @Max(1000) Integer unitsPerPack,
        @Size(max = 200) String description,
        @Size(max = 2000) String notes
) {
}

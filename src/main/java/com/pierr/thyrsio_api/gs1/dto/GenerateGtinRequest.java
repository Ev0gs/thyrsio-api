package com.pierr.thyrsio_api.gs1.dto;

import com.pierr.thyrsio_api.gs1.GtinPackaging;
import jakarta.validation.constraints.*;

import java.util.UUID;

/** Générer le gencode suivant d'un préfixe (null : le premier préfixe actif qui a de la place). */
public record GenerateGtinRequest(
        UUID prefixId,
        @NotNull UUID finishedArticleId,
        @NotNull GtinPackaging packaging,
        @Positive @Max(1000) Integer unitsPerPack,
        @Size(max = 200) String description,
        @Size(max = 2000) String notes
) {
}

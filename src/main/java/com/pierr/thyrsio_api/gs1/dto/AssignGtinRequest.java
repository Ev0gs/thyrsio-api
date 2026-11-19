package com.pierr.thyrsio_api.gs1.dto;

import com.pierr.thyrsio_api.gs1.GtinPackaging;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Attribuer un gencode réservé à un produit fini. */
public record AssignGtinRequest(
        @NotNull UUID finishedArticleId,
        @NotNull GtinPackaging packaging,
        @Positive @Max(1000) Integer unitsPerPack
) {
}

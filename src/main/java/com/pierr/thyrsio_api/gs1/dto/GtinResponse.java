package com.pierr.thyrsio_api.gs1.dto;

import com.pierr.thyrsio_api.gs1.GtinPackaging;
import com.pierr.thyrsio_api.gs1.GtinStatus;

import java.time.Instant;
import java.util.UUID;

public record GtinResponse(
        UUID id,
        String gtin,
        UUID prefixId,
        String prefix,
        UUID finishedArticleId,
        String finishedArticleCode,
        String finishedArticleLabel,
        GtinPackaging packaging,
        String packagingLabel,
        int unitsPerPack,
        String description,
        GtinStatus status,
        String notes,
        Instant createdAt,
        Instant assignedAt,
        Instant retiredAt
) {
}

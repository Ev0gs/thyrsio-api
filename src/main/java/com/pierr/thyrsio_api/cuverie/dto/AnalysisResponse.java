package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.Tank;
import com.pierr.thyrsio_api.cuverie.WineAnalysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AnalysisResponse(
        UUID id,
        UUID wineLotId,
        UUID tankId,
        String tankCode,
        LocalDate analyzedOn,
        BigDecimal alcohol,
        BigDecimal freeSo2,
        BigDecimal totalSo2,
        BigDecimal totalAcidity,
        BigDecimal volatileAcidity,
        String laboratory,
        String notes,
        UUID createdBy,
        Instant createdAt
) {
    public static AnalysisResponse from(WineAnalysis analysis) {
        Tank tank = analysis.getTank();
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getWineLot().getId(),
                tank != null ? tank.getId() : null,
                tank != null ? tank.getCode() : null,
                analysis.getAnalyzedOn(),
                analysis.getAlcohol(),
                analysis.getFreeSo2(),
                analysis.getTotalSo2(),
                analysis.getTotalAcidity(),
                analysis.getVolatileAcidity(),
                analysis.getLaboratory(),
                analysis.getNotes(),
                analysis.getCreatedBy(),
                analysis.getCreatedAt()
        );
    }
}

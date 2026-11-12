package com.pierr.thyrsio_api.production.dto;

import com.pierr.thyrsio_api.cuverie.dto.WineLotSummary;
import com.pierr.thyrsio_api.production.BottlingLocation;
import com.pierr.thyrsio_api.production.BottlingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Une mise dans le planning ou une liste.
 * shortageCount : le nombre de matières qui manquent (mises planifiées seulement).
 * alert : planifiée, avec des manquants, et elle commence dans moins de N jours (10 par défaut).
 * wineLot : le vin actuellement dans la cuve d'origine (mise chez nous).
 */
public record BottlingRow(
        UUID id,
        String code,
        BottlingStatus status,
        BottlingLocation location,
        String contractor,
        LocalDate startDate,
        LocalDate endDate,
        UUID finishedArticleId,
        String finishedArticleCode,
        String finishedArticleLabel,
        int plannedBottles,
        Integer producedBottles,
        int bottleVolumeCl,
        BigDecimal wineNeededHl,
        UUID sourceTankId,
        String sourceTankCode,
        WineLotSummary wineLot,
        String wineDescription,
        int shortageCount,
        boolean alert,
        String lotNumber,
        List<AllocationLine> allocations
) {
}

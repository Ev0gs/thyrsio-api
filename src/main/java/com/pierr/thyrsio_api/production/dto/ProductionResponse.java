package com.pierr.thyrsio_api.production.dto;

import com.pierr.thyrsio_api.cuverie.dto.WineLotSummary;
import com.pierr.thyrsio_api.production.ProductionSlot;
import com.pierr.thyrsio_api.production.ProductionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Une production d'une mise, avec son numéro de lot, ses matières et, une fois clôturée, son réel. */
public record ProductionResponse(
        UUID id,
        ProductionStatus status,
        LocalDate startDate,
        LocalDate endDate,
        ProductionSlot slot,
        UUID finishedArticleId,
        String finishedArticleCode,
        String finishedArticleLabel,
        int plannedBottles,
        int bottleVolumeCl,
        BigDecimal wineNeededHl,
        UUID sourceTankId,
        String sourceTankCode,
        BigDecimal tankVolumeHl,
        WineLotSummary wineLot,
        String wineDescription,
        UUID appellationId,
        String appellationCode,
        String appellationName,
        Integer vintage,
        String lotNumber,
        List<MaterialLine> materials,
        List<AllocationLine> allocations,
        int shortageCount,
        boolean alert,
        Integer producedBottles,
        Integer brokenBottles,
        BigDecimal wineUsedHl,
        List<ConsumptionLine> consumptions,
        Instant closedAt,
        UUID closedBy
) {
    /**
     * Une matière prévue. Pour une production planifiée : le stock actuel, ce que les productions précédentes
     * réservent déjà, et ce qui manque. null pour une production clôturée ou annulée.
     */
    public record MaterialLine(
            UUID articleId,
            String articleCode,
            String articleLabel,
            String categoryLabel,
            String unitCode,
            BigDecimal plannedQuantity,
            BigDecimal stockQuantity,
            BigDecimal reservedBefore,
            BigDecimal shortageQuantity
    ) {
    }

    /** Ce qui a été consommé, par matière. */
    public record ConsumptionLine(
            UUID articleId,
            String articleCode,
            String articleLabel,
            String unitCode,
            BigDecimal quantity
    ) {
    }
}

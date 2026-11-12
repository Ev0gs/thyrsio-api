package com.pierr.thyrsio_api.production.dto;

import com.pierr.thyrsio_api.cuverie.dto.WineLotSummary;
import com.pierr.thyrsio_api.production.BottlingLocation;
import com.pierr.thyrsio_api.production.BottlingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** La fiche complète d'une mise : le planning, les matières avec leurs manquants, et le réel une fois clôturée. */
public record BottlingResponse(
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
        int bottleVolumeCl,
        BigDecimal wineNeededHl,
        UUID sourceTankId,
        String sourceTankCode,
        // Le volume actuellement dans la cuve d'origine : pour vérifier qu'il y a assez de vin
        BigDecimal tankVolumeHl,
        WineLotSummary wineLot,
        String wineDescription,
        String notes,
        List<MaterialLine> materials,
        // Les commandes servies ; le reste des bouteilles va au stock
        List<AllocationLine> allocations,
        int shortageCount,
        boolean alert,
        // --- Le réel ---
        Integer producedBottles,
        Integer brokenBottles,
        BigDecimal wineUsedHl,
        String lotNumber,
        List<ConsumptionLine> consumptions,
        Instant closedAt,
        UUID closedBy,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Une matière prévue. Pour une mise planifiée : le stock actuel, ce que les mises précédentes
     * réservent déjà, et ce qui manque. null pour une mise clôturée ou annulée.
     */
    public record MaterialLine(
            UUID articleId,
            String articleCode,
            String articleLabel,
            String categoryLabel,
            String unitCode,
            boolean lotTracked,
            BigDecimal plannedQuantity,
            BigDecimal stockQuantity,
            BigDecimal reservedBefore,
            BigDecimal shortageQuantity
    ) {
    }

    /** Ce qui a été consommé, par lot. */
    public record ConsumptionLine(
            UUID articleId,
            String articleCode,
            String articleLabel,
            String unitCode,
            UUID lotId,
            String lotNumber,
            BigDecimal quantity
    ) {
    }
}

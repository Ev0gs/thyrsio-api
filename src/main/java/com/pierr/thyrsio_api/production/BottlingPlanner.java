package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.stock.StockLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Le calcul des manquants. Les productions planifiées sont prises dans l'ordre où elles auront lieu
 * (jour, puis matin avant après-midi) : chacune "réserve" ce dont elle a besoin, et la suivante ne voit
 * que ce qui reste. Exemple : 5 000 bouchons en stock, une production lundi qui en prévoit 4 000 et une
 * jeudi qui en prévoit 3 000 : lundi est complète, jeudi manque de 2 000.
 */
@Component
class BottlingPlanner {

    /** Une matière d'une production planifiée, et ce qu'il en manque. */
    record MaterialNeed(UUID articleId, BigDecimal planned, BigDecimal stock,
                        BigDecimal reservedBefore, BigDecimal shortage) {
        boolean isShort() {
            return shortage.signum() > 0;
        }
    }

    static final Comparator<BottlingProduction> CHRONOLOGICAL = Comparator
            .comparing(BottlingProduction::getStartDate)
            .thenComparingInt(p -> p.getSlot().rank())
            .thenComparing(BottlingProduction::getCreatedAt);

    private final BottlingProductionRepository productionRepository;
    private final StockLookup stockLookup;

    BottlingPlanner(BottlingProductionRepository productionRepository, StockLookup stockLookup) {
        this.productionRepository = productionRepository;
        this.stockLookup = stockLookup;
    }

    /** Les besoins de chaque production planifiée, indexés par id de production. */
    Map<UUID, List<MaterialNeed>> plan() {
        List<BottlingProduction> planned = new ArrayList<>(
                productionRepository.findByStatusWithBottling(ProductionStatus.PLANNED));
        planned.sort(CHRONOLOGICAL);
        Set<UUID> articleIds = new HashSet<>();
        planned.forEach(production -> production.getMaterials().forEach(m -> articleIds.add(m.getArticleId())));
        Map<UUID, BigDecimal> stock = stockLookup.totalsByArticle(articleIds);

        Map<UUID, BigDecimal> reserved = new HashMap<>();
        Map<UUID, List<MaterialNeed>> needs = new HashMap<>();
        for (BottlingProduction production : planned) {
            List<MaterialNeed> lines = new ArrayList<>();
            for (BottlingMaterial material : production.getMaterials()) {
                UUID articleId = material.getArticleId();
                BigDecimal inStock = stock.getOrDefault(articleId, BigDecimal.ZERO);
                BigDecimal before = reserved.getOrDefault(articleId, BigDecimal.ZERO);
                BigDecimal available = inStock.subtract(before).max(BigDecimal.ZERO);
                BigDecimal shortage = material.getPlannedQuantity().subtract(available).max(BigDecimal.ZERO);
                lines.add(new MaterialNeed(articleId, material.getPlannedQuantity(), inStock, before, shortage));
                reserved.put(articleId, before.add(material.getPlannedQuantity()));
            }
            needs.put(production.getId(), lines);
        }
        return needs;
    }
}

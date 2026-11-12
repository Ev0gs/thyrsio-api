package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.stock.StockLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Le calcul des manquants. Les mises planifiées sont prises dans l'ordre de leur date :
 * chacune "réserve" ce dont elle a besoin, et la suivante ne voit que ce qui reste.
 * Exemple : 5 000 bouchons en stock, une mise lundi qui en prévoit 4 000 et une jeudi qui en prévoit 3 000 :
 * lundi est complète, jeudi manque de 2 000.
 */
@Component
class BottlingPlanner {

    /** Une matière d'une mise planifiée, et ce qu'il en manque. */
    record MaterialNeed(UUID articleId, BigDecimal planned, BigDecimal stock,
                        BigDecimal reservedBefore, BigDecimal shortage) {
        boolean isShort() {
            return shortage.signum() > 0;
        }
    }

    private final BottlingRepository bottlingRepository;
    private final StockLookup stockLookup;

    BottlingPlanner(BottlingRepository bottlingRepository, StockLookup stockLookup) {
        this.bottlingRepository = bottlingRepository;
        this.stockLookup = stockLookup;
    }

    /** Les besoins de chaque mise planifiée, indexés par id de mise. 2 requêtes, quelle que soit la quantité de mises. */
    Map<UUID, List<MaterialNeed>> plan() {
        List<Bottling> planned = bottlingRepository.findByStatusOrderByStartDateAscCreatedAtAsc(BottlingStatus.PLANNED);
        Set<UUID> articleIds = new HashSet<>();
        planned.forEach(bottling -> bottling.getMaterials().forEach(m -> articleIds.add(m.getArticleId())));
        Map<UUID, BigDecimal> stock = stockLookup.totalsByArticle(articleIds);

        Map<UUID, BigDecimal> reserved = new HashMap<>();
        Map<UUID, List<MaterialNeed>> needs = new HashMap<>();
        for (Bottling bottling : planned) {
            List<MaterialNeed> lines = new ArrayList<>();
            for (BottlingMaterial material : bottling.getMaterials()) {
                UUID articleId = material.getArticleId();
                BigDecimal inStock = stock.getOrDefault(articleId, BigDecimal.ZERO);
                BigDecimal before = reserved.getOrDefault(articleId, BigDecimal.ZERO);
                BigDecimal available = inStock.subtract(before).max(BigDecimal.ZERO);
                BigDecimal shortage = material.getPlannedQuantity().subtract(available).max(BigDecimal.ZERO);
                lines.add(new MaterialNeed(articleId, material.getPlannedQuantity(), inStock, before, shortage));
                reserved.put(articleId, before.add(material.getPlannedQuantity()));
            }
            needs.put(bottling.getId(), lines);
        }
        return needs;
    }
}

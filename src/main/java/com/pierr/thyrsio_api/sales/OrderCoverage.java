package com.pierr.thyrsio_api.sales;

import com.pierr.thyrsio_api.stock.StockLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comment chaque ligne de commande ouverte sera servie : par le stock de produits finis, par une mise
 * planifiée, ou pas encore (« à planifier »). Les lignes sont servies dans l'ordre de leur date de chargement.
 *
 * 1. Les bouteilles d'une mise clôturée pour une commande lui sont réservées en priorité.
 * 2. Puis les mises planifiées pour cette ligne.
 * 3. Puis le stock restant, au premier qui le demande.
 * Ce qui reste est à planifier.
 */
@Component
class OrderCoverage {

    /** La couverture d'une ligne : remaining = fromStock + fromBottlings + missing. */
    record Coverage(int remaining, int fromStock, int fromBottlings, int missing) {
    }

    private final CustomerOrderLineRepository lineRepository;
    private final StockLookup stockLookup;
    private final PlannedSupply plannedSupply;

    OrderCoverage(CustomerOrderLineRepository lineRepository, StockLookup stockLookup, PlannedSupply plannedSupply) {
        this.lineRepository = lineRepository;
        this.stockLookup = stockLookup;
        this.plannedSupply = plannedSupply;
    }

    /** La couverture de toutes les lignes ouvertes, indexée par id de ligne. 3 requêtes au total. */
    Map<UUID, Coverage> compute() {
        List<CustomerOrderLine> lines = lineRepository.findOpenLines(
                List.of(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_SHIPPED));
        if (lines.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> stockLeft = new HashMap<>();
        stockLookup.totalsByArticle(lines.stream().map(CustomerOrderLine::getArticleId).collect(Collectors.toSet()))
                .forEach((articleId, quantity) -> stockLeft.put(articleId, toBottles(quantity)));

        Map<UUID, List<PlannedSupply.Allocation>> allocations = plannedSupply
                .allocationsFor(lines.stream().map(CustomerOrderLine::getId).toList()).stream()
                .collect(Collectors.groupingBy(PlannedSupply.Allocation::orderLineId));

        // 1. Les bouteilles déjà produites pour une ligne lui reviennent d'abord
        Map<UUID, Integer> reserved = new HashMap<>();
        for (CustomerOrderLine line : lines) {
            int produced = allocations.getOrDefault(line.getId(), List.of()).stream()
                    .filter(PlannedSupply.Allocation::closed).mapToInt(PlannedSupply.Allocation::quantity).sum();
            int take = Math.min(Math.min(produced, line.remaining()), stockLeft.getOrDefault(line.getArticleId(), 0));
            if (take > 0) {
                reserved.put(line.getId(), take);
                stockLeft.merge(line.getArticleId(), -take, Integer::sum);
            }
        }

        // 2 et 3. Les mises planifiées, puis le stock restant, dans l'ordre des dates de chargement
        Map<UUID, Coverage> coverage = new HashMap<>();
        for (CustomerOrderLine line : lines) {
            int remaining = line.remaining();
            int fromReserved = reserved.getOrDefault(line.getId(), 0);
            int rest = remaining - fromReserved;
            int planned = allocations.getOrDefault(line.getId(), List.of()).stream()
                    .filter(PlannedSupply.Allocation::planned).mapToInt(PlannedSupply.Allocation::quantity).sum();
            int fromBottlings = Math.min(rest, planned);
            rest -= fromBottlings;
            int fromStock = Math.min(rest, Math.max(0, stockLeft.getOrDefault(line.getArticleId(), 0)));
            stockLeft.merge(line.getArticleId(), -fromStock, Integer::sum);
            rest -= fromStock;
            coverage.put(line.getId(), new Coverage(remaining, fromReserved + fromStock, fromBottlings, rest));
        }
        return coverage;
    }

    /** Le stock d'un produit fini est un nombre entier de bouteilles. */
    private static int toBottles(BigDecimal quantity) {
        return quantity.intValue();
    }
}

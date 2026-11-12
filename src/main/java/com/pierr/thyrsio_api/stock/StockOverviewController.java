package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.stock.dto.StockOverviewRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Stock — Soldes")
@RestController
@RequestMapping("/api/stock/overview")
public class StockOverviewController {

    private final StockOverviewService overviewService;

    public StockOverviewController(StockOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @Operation(summary = "Vue d'ensemble du stock : une ligne par article",
            description = "Stock total (tous lots), seuil d'alerte, état et date du dernier mouvement. "
                    + "Non paginé : prévu pour quelques centaines d'articles, filtrés par l'écran. "
                    + "Le détail par lot : GET /api/stock/balances?articleId=... "
                    + "Matières sèches par défaut ; finishedGoods=true pour les produits finis.")
    @GetMapping
    public List<StockOverviewRow> overview(@RequestParam(defaultValue = "false") boolean includeArchived,
                                           @RequestParam(defaultValue = "false") boolean finishedGoods) {
        return overviewService.overview(includeArchived, finishedGoods);
    }
}
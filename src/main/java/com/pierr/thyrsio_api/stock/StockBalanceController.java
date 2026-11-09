package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.stock.dto.BalanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Stock — Soldes")
@RestController
@RequestMapping("/api/stock/balances")
public class StockBalanceController {

    private final StockService stockService;

    public StockBalanceController(StockService stockService) {
        this.stockService = stockService;
    }

    @Operation(summary = "Stock actuel par article et par lot",
            description = "Les soldes à zéro sont exclus, sauf avec includeEmpty=true. Lecture seule : le stock ne bouge que par des mouvements.")
    @GetMapping
    public Page<BalanceResponse> list(
            @RequestParam(required = false) UUID articleId,
            @RequestParam(defaultValue = "false") boolean includeEmpty,
            @ParameterObject @PageableDefault(size = 50, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return stockService.listBalances(articleId, includeEmpty, pageable);
    }
}
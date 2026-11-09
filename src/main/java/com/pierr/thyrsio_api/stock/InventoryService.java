package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.referentiel.ArticleLookup;
import com.pierr.thyrsio_api.stock.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockService stockService;
    private final ArticleLookup articleLookup;

    public InventoryService(InventoryRepository inventoryRepository,
                            StockBalanceRepository balanceRepository,
                            StockService stockService,
                            ArticleLookup articleLookup) {
        this.inventoryRepository = inventoryRepository;
        this.balanceRepository = balanceRepository;
        this.stockService = stockService;
        this.articleLookup = articleLookup;
    }

    // ========== Lecture ==========

    @Transactional(readOnly = true)
    public Page<InventorySummaryResponse> list(InventoryStatus status, Pageable pageable) {
        Page<Inventory> page = (status == null)
                ? inventoryRepository.findAll(pageable)
                : inventoryRepository.findByStatus(status, pageable);
        return page.map(InventorySummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(UUID id) {
        return toResponse(findInventory(id));
    }

    // ========== Brouillon ==========

    @Transactional
    public InventoryResponse create(CreateInventoryRequest request, UUID userId) {
        Inventory inventory = new Inventory(
                request.label().trim(),
                request.countedAt() != null ? request.countedAt() : Instant.now(),
                blankToNull(request.notes()),
                userId);
        if (Boolean.TRUE.equals(request.prefill())) {
            for (StockBalance balance : balanceRepository.findByQuantityGreaterThan(BigDecimal.ZERO)) {
                inventory.addLine(balance.getArticleId(), balance.getLot());
            }
        }
        // save() d'une nouvelle entité : la cascade enregistre aussi les lignes, avec leurs ids
        inventoryRepository.save(inventory);
        return toResponse(inventory);
    }

    @Transactional
    public InventoryResponse addLine(UUID inventoryId, AddInventoryLineRequest request) {
        Inventory inventory = findInventory(inventoryId);
        ArticleInfo article = findArticle(request.articleId());
        Lot lot = stockService.resolveLot(article, request.lotId());
        if (request.countedQuantity() != null) {
            StockService.checkDecimals(request.countedQuantity(), article);
        }

        InventoryLine line = inventory.addLine(article.id(), lot);
        if (request.countedQuantity() != null) {
            inventory.count(line, request.countedQuantity(), blankToNull(request.notes()));
        }
        // Une ligne ajoutée à un inventaire déjà en base n'est insérée qu'au flush :
        // on le force pour que la réponse contienne l'id de la nouvelle ligne
        inventoryRepository.flush();
        return toResponse(inventory);
    }

    @Transactional
    public InventoryResponse countLine(UUID inventoryId, UUID lineId, CountInventoryLineRequest request) {
        Inventory inventory = findInventory(inventoryId);
        InventoryLine line = findLine(inventory, lineId);
        StockService.checkDecimals(request.countedQuantity(), findArticle(line.getArticleId()));
        inventory.count(line, request.countedQuantity(), blankToNull(request.notes()));
        return toResponse(inventory);
    }

    @Transactional
    public void removeLine(UUID inventoryId, UUID lineId) {
        Inventory inventory = findInventory(inventoryId);
        inventory.removeLine(findLine(inventory, lineId));
    }

    // ========== Clôture ==========

    @Transactional
    public InventoryResponse validate(UUID inventoryId, UUID userId) {
        Inventory inventory = findInventory(inventoryId);
        inventory.requireDraft();
        List<InventoryLine> lines = inventory.getLines();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("L'inventaire ne contient aucune ligne");
        }
        long notCounted = lines.stream().filter(line -> line.getCountedQuantity() == null).count();
        if (notCounted > 0) {
            throw new BusinessRuleException(notCounted + " ligne(s) pas encore comptée(s) : saisissez 0 si l'article est absent");
        }

        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(articleIdsOf(inventory));

        // Toujours verrouiller les soldes dans le même ordre (article, puis lot) :
        // deux validations simultanées ne peuvent pas s'attendre mutuellement (deadlock)
        List<InventoryLine> ordered = lines.stream()
                .sorted(Comparator.comparing(InventoryLine::getArticleId)
                        .thenComparing(InventoryLine::getLotId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        for (InventoryLine line : ordered) {
            StockService.InventoryAdjustment result = stockService.adjustTo(
                    articles.get(line.getArticleId()), line.getLot(), line.getCountedQuantity(),
                    inventory.getCountedAt(), inventory.getLabel(), userId);
            line.recordResult(result.expected(), result.delta());
        }
        inventory.markValidated(userId);
        return toResponse(inventory);
    }

    @Transactional
    public InventoryResponse cancel(UUID inventoryId) {
        Inventory inventory = findInventory(inventoryId);
        inventory.cancel();
        return toResponse(inventory);
    }

    // ========== Méthodes utilitaires ==========

    /** Une clé de Map composée : un record a equals() et hashCode() tout faits. */
    private record BalanceKey(UUID articleId, UUID lotId) {
    }

    private InventoryResponse toResponse(Inventory inventory) {
        Set<UUID> articleIds = articleIdsOf(inventory);
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(articleIds);
        boolean draft = inventory.getStatus() == InventoryStatus.DRAFT;
        Map<BalanceKey, BigDecimal> currentStock = draft ? currentStock(articleIds) : Map.of();

        List<InventoryLineResponse> lines = inventory.getLines().stream()
                .map(line -> {
                    BigDecimal theoretical = draft
                            ? currentStock.getOrDefault(new BalanceKey(line.getArticleId(), line.getLotId()), BigDecimal.ZERO)
                            : line.getExpectedQuantity();
                    BigDecimal difference = draft
                            ? (line.getCountedQuantity() != null ? line.getCountedQuantity().subtract(theoretical) : null)
                            : line.getAdjustment();
                    return InventoryLineResponse.from(line, articles.get(line.getArticleId()), theoretical, difference);
                })
                .sorted(Comparator.comparing(InventoryLineResponse::articleCode)
                        .thenComparing(InventoryLineResponse::lotNumber, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        return InventoryResponse.from(inventory, lines);
    }

    /** Le stock actuel de ces articles, en une requête, indexé par (article, lot). */
    private Map<BalanceKey, BigDecimal> currentStock(Set<UUID> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        return balanceRepository.findByArticleIdIn(articleIds).stream()
                .collect(Collectors.toMap(
                        balance -> new BalanceKey(balance.getArticleId(),
                                balance.getLot() != null ? balance.getLot().getId() : null),
                        StockBalance::getQuantity));
    }

    private static Set<UUID> articleIdsOf(Inventory inventory) {
        return inventory.getLines().stream().map(InventoryLine::getArticleId).collect(Collectors.toSet());
    }

    private Inventory findInventory(UUID id) {
        return inventoryRepository.findWithLinesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventaire", id));
    }

    private static InventoryLine findLine(Inventory inventory, UUID lineId) {
        return inventory.findLine(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("Ligne d'inventaire", lineId));
    }

    private ArticleInfo findArticle(UUID articleId) {
        return articleLookup.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + articleId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
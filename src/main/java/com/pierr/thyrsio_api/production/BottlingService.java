package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.cuverie.CuverieService;
import com.pierr.thyrsio_api.cuverie.TankInfo;
import com.pierr.thyrsio_api.cuverie.TankLookup;
import com.pierr.thyrsio_api.production.BottlingPlanner.MaterialNeed;
import com.pierr.thyrsio_api.production.dto.*;
import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.referentiel.ArticleLookup;
import com.pierr.thyrsio_api.referentiel.ClientInfo;
import com.pierr.thyrsio_api.referentiel.ClientLookup;
import com.pierr.thyrsio_api.sales.OrderInfo;
import com.pierr.thyrsio_api.sales.OrderLookup;
import com.pierr.thyrsio_api.sales.OrderStatus;
import com.pierr.thyrsio_api.stock.LotService;
import com.pierr.thyrsio_api.stock.MovementType;
import com.pierr.thyrsio_api.stock.StockLookup;
import com.pierr.thyrsio_api.stock.StockMovementCommand;
import com.pierr.thyrsio_api.stock.StockService;
import com.pierr.thyrsio_api.stock.dto.CreateLotRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Les mises en bouteille. Ce module orchestre les autres : à la clôture, il consomme les matières
 * sèches (Stock), tire le vin de la cuve (Cuverie) et fait entrer les bouteilles en stock (Stock),
 * le tout dans une seule transaction : si une étape échoue, rien n'est enregistré.
 */
@Service
public class BottlingService {

    /** Les dates du planning sont des dates françaises. */
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final BottlingRepository bottlingRepository;
    private final BottlingPlanner planner;
    private final ArticleLookup articleLookup;
    private final TankLookup tankLookup;
    private final StockLookup stockLookup;
    private final StockService stockService;
    private final LotService lotService;
    private final CuverieService cuverieService;
    private final OrderLookup orderLookup;
    private final ClientLookup clientLookup;
    private final BottlingAllocationRepository allocationRepository;
    private final int alertDays;

    public BottlingService(BottlingRepository bottlingRepository,
                           BottlingPlanner planner,
                           ArticleLookup articleLookup,
                           TankLookup tankLookup,
                           StockLookup stockLookup,
                           StockService stockService,
                           LotService lotService,
                           CuverieService cuverieService,
                           OrderLookup orderLookup,
                           ClientLookup clientLookup,
                           BottlingAllocationRepository allocationRepository,
                           // Combien de jours avant une mise on veut être alerté des manquants
                           @Value("${thyrsio.production.alert-days:10}") int alertDays) {
        this.bottlingRepository = bottlingRepository;
        this.planner = planner;
        this.articleLookup = articleLookup;
        this.tankLookup = tankLookup;
        this.stockLookup = stockLookup;
        this.stockService = stockService;
        this.lotService = lotService;
        this.cuverieService = cuverieService;
        this.orderLookup = orderLookup;
        this.clientLookup = clientLookup;
        this.allocationRepository = allocationRepository;
        this.alertDays = alertDays;
    }

    // ========== Lecture ==========

    /**
     * Les mises qui touchent la période [from, to] (une mise de plusieurs jours est comprise dès qu'un de ses
     * jours y tombe), filtrables par statut. Sans dates : toutes.
     */
    @Transactional(readOnly = true)
    public Page<BottlingRow> list(LocalDate from, LocalDate to, BottlingStatus status, Pageable pageable) {
        List<Specification<Bottling>> filters = new ArrayList<>();
        if (from != null) filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), from));
        if (to != null) filters.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to));
        if (status != null) filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        Page<Bottling> page = bottlingRepository.findAll(Specification.allOf(filters), pageable);

        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                page.stream().map(Bottling::getFinishedArticleId).collect(Collectors.toSet()));
        Map<UUID, TankInfo> tanks = tankLookup.findAllById(page.stream()
                .map(Bottling::getSourceTankId).filter(Objects::nonNull).collect(Collectors.toSet()));
        boolean anyPlanned = page.stream().anyMatch(b -> b.getStatus() == BottlingStatus.PLANNED);
        Map<UUID, List<MaterialNeed>> needs = anyPlanned ? planner.plan() : Map.of();
        Map<UUID, List<AllocationLine>> allocations = allocationViews(
                allocationRepository.findByBottlingIdIn(page.stream().map(Bottling::getId).toList()));

        return page.map(bottling -> {
            ArticleInfo article = articles.get(bottling.getFinishedArticleId());
            TankInfo tank = bottling.getSourceTankId() != null ? tanks.get(bottling.getSourceTankId()) : null;
            int shortages = shortageCount(needs.get(bottling.getId()));
            return new BottlingRow(
                    bottling.getId(), bottling.getCode(), bottling.getStatus(), bottling.getLocation(),
                    bottling.getContractor(), bottling.getStartDate(), bottling.getEndDate(),
                    bottling.getFinishedArticleId(), article != null ? article.code() : null,
                    article != null ? article.label() : null,
                    bottling.getPlannedBottles(), bottling.getProducedBottles(), bottling.getBottleVolumeCl(),
                    bottling.wineNeededHl(), bottling.getSourceTankId(), tank != null ? tank.code() : null,
                    tank != null ? tank.wineLot() : null, bottling.getWineDescription(),
                    shortages, isAlert(bottling, shortages), bottling.getLotNumber(),
                    allocations.getOrDefault(bottling.getId(), List.of()));
        });
    }

    @Transactional(readOnly = true)
    public BottlingResponse get(UUID id) {
        return toResponse(findBottling(id));
    }

    // ========== Planification ==========

    @Transactional
    public BottlingResponse create(BottlingRequest request, UUID userId) {
        Bottling bottling = new Bottling(nextCode(request.startDate()), userId);
        applyPlan(bottling, request);
        bottlingRepository.save(bottling);
        return toResponse(bottling);
    }

    @Transactional
    public BottlingResponse update(UUID id, BottlingRequest request) {
        Bottling bottling = findBottling(id);
        applyPlan(bottling, request);
        // Les nouvelles matières ne sont insérées qu'au flush : on le force pour calculer les manquants
        bottlingRepository.flush();
        return toResponse(bottling);
    }

    @Transactional
    public BottlingResponse cancel(UUID id) {
        Bottling bottling = findBottling(id);
        bottling.cancel();
        return toResponse(bottling);
    }

    // ========== Clôture ==========

    @Transactional
    public BottlingResponse close(UUID id, CloseBottlingRequest request, UUID userId) {
        Bottling bottling = findBottling(id);
        bottling.requirePlanned();
        LocalDate today = LocalDate.now(ZONE);
        if (bottling.getStartDate().isAfter(today)) {
            throw new BusinessRuleException("La mise " + bottling.getCode() + " commence le "
                    + bottling.getStartDate() + " : elle ne peut pas être clôturée avant");
        }
        if (bottling.getLocation() == BottlingLocation.ON_SITE && request.wineUsedHl() == null) {
            throw new IllegalArgumentException("Indiquez le volume de vin tiré de la cuve");
        }
        if (bottling.getLocation() == BottlingLocation.AT_PRODUCER && request.wineUsedHl() != null) {
            throw new IllegalArgumentException("Mise chez le producteur : le vin ne sort pas de nos cuves");
        }
        ArticleInfo finished = articleLookup.findById(bottling.getFinishedArticleId()).orElseThrow();

        // Le dernier jour de la mise, en fin de journée (jamais dans le futur) : la date des mouvements
        Instant endOfMise = bottling.getEndDate().atTime(LocalTime.of(17, 0)).atZone(ZONE).toInstant();
        Instant occurredAt = endOfMise.isBefore(Instant.now()) ? endOfMise : Instant.now();
        String reference = "Mise " + bottling.getCode();

        // 1. Les matières sèches consommées, dans un ordre fixe (article puis lot) pour éviter les interblocages
        List<ConsumptionRequest> consumptions = request.consumptions().stream()
                .sorted(Comparator.comparing(ConsumptionRequest::articleId)
                        .thenComparing(ConsumptionRequest::lotId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        List<Bottling.Consumed> consumed = new ArrayList<>();
        for (ConsumptionRequest line : consumptions) {
            ArticleInfo article = articleLookup.findById(line.articleId())
                    .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + line.articleId()));
            if (article.finishedGood()) {
                throw new IllegalArgumentException(article.code() + " est un produit fini, pas une matière sèche");
            }
            // StockService vérifie le lot, les décimales et le stock disponible (409 si insuffisant)
            stockService.record(new StockMovementCommand(line.articleId(), line.lotId(), MovementType.CONSUMPTION,
                    line.quantity(), occurredAt, reference, null, userId));
            consumed.add(new Bottling.Consumed(line.articleId(), line.lotId(), line.quantity()));
        }

        // 2. Le vin tiré de la cuve
        if (bottling.getLocation() == BottlingLocation.ON_SITE) {
            cuverieService.recordBottling(bottling.getSourceTankId(), request.wineUsedHl(), occurredAt, reference, userId);
        }

        // 3. Les bouteilles produites entrent en stock, sous un nouveau numéro de lot
        String lotNumber = nextLotNumber(bottling.getEndDate());
        UUID lotId = lotService.create(new CreateLotRequest(finished.id(), lotNumber, null, reference)).id();
        stockService.record(new StockMovementCommand(finished.id(), lotId, MovementType.PRODUCTION,
                BigDecimal.valueOf(request.producedBottles()), occurredAt, reference, null, userId));

        bottling.close(request.producedBottles(),
                request.brokenBottles() != null ? request.brokenBottles() : 0,
                request.wineUsedHl(), lotNumber, consumed, userId);
        bottlingRepository.flush();
        return toResponse(bottling);
    }

    // ========== Méthodes utilitaires ==========

    /** Vérifie et applique le planning, à la création comme à la modification. */
    private void applyPlan(Bottling bottling, BottlingRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("La date de fin est antérieure à la date de début");
        }
        ArticleInfo finished = articleLookup.findById(request.finishedArticleId())
                .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + request.finishedArticleId()));
        if (!finished.finishedGood()) {
            throw new IllegalArgumentException(finished.code() + " n'est pas un produit fini (catégorie "
                    + ArticleInfo.FINISHED_GOODS_CATEGORY + ")");
        }
        if (!finished.active() && !finished.id().equals(bottling.getFinishedArticleId())) {
            throw new BusinessRuleException("Le produit " + finished.code() + " est archivé");
        }

        UUID tankId = null;
        if (request.location() == BottlingLocation.ON_SITE) {
            if (request.sourceTankId() == null) {
                throw new IllegalArgumentException("Une mise chez nous tire son vin d'une cuve : choisissez-la");
            }
            TankInfo tank = tankLookup.findById(request.sourceTankId())
                    .orElseThrow(() -> new IllegalArgumentException("Cuve inconnue : " + request.sourceTankId()));
            if (!tank.active()) {
                throw new BusinessRuleException("La cuve " + tank.code() + " est archivée");
            }
            tankId = tank.id();
        }

        // Les matières : un article une seule fois, une matière sèche active, les décimales de son unité
        List<MaterialRequest> lines = request.materials() != null ? request.materials() : List.of();
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                lines.stream().map(MaterialRequest::articleId).collect(Collectors.toSet()));
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        for (MaterialRequest line : lines) {
            ArticleInfo article = articles.get(line.articleId());
            if (article == null) {
                throw new IllegalArgumentException("Article inconnu : " + line.articleId());
            }
            if (article.finishedGood()) {
                throw new IllegalArgumentException(article.code() + " est un produit fini, pas une matière sèche");
            }
            if (line.plannedQuantity().stripTrailingZeros().scale() > article.unitDecimals()) {
                throw new IllegalArgumentException("Trop de décimales pour " + article.code() + " ("
                        + article.unitCode() + ") : " + article.unitDecimals() + " au maximum");
            }
            if (quantities.put(article.id(), line.plannedQuantity()) != null) {
                throw new IllegalArgumentException("L'article " + article.code() + " figure deux fois dans la liste");
            }
        }

        bottling.plan(request.location(), blankToNull(request.contractor()), request.startDate(), request.endDate(),
                finished.id(), request.plannedBottles(), request.bottleVolumeCl(), tankId,
                request.location() == BottlingLocation.AT_PRODUCER ? blankToNull(request.wineDescription()) : null,
                blankToNull(request.notes()));
        bottling.replaceMaterials(quantities);
        bottling.replaceAllocations(checkAllocations(bottling, finished, request));
    }

    /**
     * Les commandes servies : chaque ligne porte sur le produit de la mise, attend encore assez de bouteilles
     * (en tenant compte des autres mises planifiées pour elle), et le total ne dépasse pas la mise.
     */
    private Map<UUID, Integer> checkAllocations(Bottling bottling, ArticleInfo finished, BottlingRequest request) {
        List<AllocationRequest> requested = request.allocations() != null ? request.allocations() : List.of();
        if (requested.isEmpty()) {
            return Map.of();
        }
        Set<UUID> lineIds = requested.stream().map(AllocationRequest::orderLineId).collect(Collectors.toSet());
        Map<UUID, OrderInfo.Line> lines = orderLookup.findLines(lineIds);
        // Ce que les AUTRES mises planifiées réservent déjà à ces lignes
        Map<UUID, Integer> elsewhere = new HashMap<>();
        allocationRepository.findForOrderLines(lineIds, BottlingStatus.CANCELLED).stream()
                .filter(a -> a.getBottling().getStatus() == BottlingStatus.PLANNED)
                .filter(a -> !a.getBottling().getId().equals(bottling.getId()))
                .forEach(a -> elsewhere.merge(a.getOrderLineId(), a.getQuantity(), Integer::sum));

        Map<UUID, Integer> bottles = new LinkedHashMap<>();
        int total = 0;
        for (AllocationRequest allocation : requested) {
            OrderInfo.Line line = lines.get(allocation.orderLineId());
            if (line == null) {
                throw new IllegalArgumentException("Ligne de commande inconnue : " + allocation.orderLineId());
            }
            if (!line.articleId().equals(finished.id())) {
                throw new IllegalArgumentException("La commande " + line.orderCode()
                        + " porte sur un autre produit que celui de la mise");
            }
            if (line.orderStatus() == OrderStatus.CANCELLED || line.orderStatus() == OrderStatus.SHIPPED) {
                throw new BusinessRuleException("La commande " + line.orderCode() + " est "
                        + (line.orderStatus() == OrderStatus.CANCELLED ? "annulée" : "déjà expédiée"));
            }
            int available = line.remaining() - elsewhere.getOrDefault(line.id(), 0);
            if (allocation.quantity() > available) {
                throw new BusinessRuleException("La commande " + line.orderCode() + " n'attend plus que "
                        + Math.max(available, 0) + " bouteilles de ce produit (autres mises comprises)");
            }
            if (bottles.put(line.id(), allocation.quantity()) != null) {
                throw new IllegalArgumentException("La commande " + line.orderCode() + " figure deux fois");
            }
            total += allocation.quantity();
        }
        if (total > request.plannedBottles()) {
            throw new IllegalArgumentException("Les commandes servies (" + total + " bouteilles) dépassent la mise ("
                    + request.plannedBottles() + ")");
        }
        return bottles;
    }

    /** Les allocations avec le code de leur commande et le nom du client, regroupées par mise. */
    private Map<UUID, List<AllocationLine>> allocationViews(Collection<BottlingAllocation> allocations) {
        if (allocations.isEmpty()) {
            return Map.of();
        }
        Map<UUID, OrderInfo.Line> lines = orderLookup.findLines(
                allocations.stream().map(BottlingAllocation::getOrderLineId).collect(Collectors.toSet()));
        Map<UUID, ClientInfo> clients = clientLookup.findAllById(
                lines.values().stream().map(OrderInfo.Line::clientId).collect(Collectors.toSet()));
        return allocations.stream().collect(Collectors.groupingBy(
                allocation -> allocation.getBottling().getId(),
                Collectors.mapping(allocation -> {
                    OrderInfo.Line line = lines.get(allocation.getOrderLineId());
                    ClientInfo client = line != null ? clients.get(line.clientId()) : null;
                    return new AllocationLine(allocation.getOrderLineId(), line != null ? line.orderId() : null,
                            line != null ? line.orderCode() : null, client != null ? client.name() : null,
                            allocation.getQuantity());
                }, Collectors.toList())));
    }

    private BottlingResponse toResponse(Bottling bottling) {
        boolean planned = bottling.getStatus() == BottlingStatus.PLANNED;
        Map<UUID, MaterialNeed> needs = planned
                ? planner.plan().getOrDefault(bottling.getId(), List.of()).stream()
                .collect(Collectors.toMap(MaterialNeed::articleId, need -> need))
                : Map.of();

        Set<UUID> articleIds = new HashSet<>();
        articleIds.add(bottling.getFinishedArticleId());
        bottling.getMaterials().forEach(material -> articleIds.add(material.getArticleId()));
        bottling.getConsumptions().forEach(line -> articleIds.add(line.getArticleId()));
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(articleIds);
        Map<UUID, String> lotNumbers = stockLookup.lotNumbers(bottling.getConsumptions().stream()
                .map(BottlingConsumption::getLotId).filter(Objects::nonNull).collect(Collectors.toSet()));
        TankInfo tank = bottling.getSourceTankId() != null
                ? tankLookup.findById(bottling.getSourceTankId()).orElse(null)
                : null;

        List<BottlingResponse.MaterialLine> materials = bottling.getMaterials().stream()
                .map(material -> {
                    ArticleInfo article = articles.get(material.getArticleId());
                    MaterialNeed need = needs.get(material.getArticleId());
                    return new BottlingResponse.MaterialLine(
                            material.getArticleId(), article.code(), article.label(), article.categoryLabel(),
                            article.unitCode(), article.lotTracked(), material.getPlannedQuantity(),
                            need != null ? need.stock() : null,
                            need != null ? need.reservedBefore() : null,
                            need != null ? need.shortage() : null);
                })
                .sorted(Comparator.comparing(BottlingResponse.MaterialLine::categoryLabel)
                        .thenComparing(BottlingResponse.MaterialLine::articleLabel))
                .toList();

        List<BottlingResponse.ConsumptionLine> consumptions = bottling.getConsumptions().stream()
                .map(line -> {
                    ArticleInfo article = articles.get(line.getArticleId());
                    return new BottlingResponse.ConsumptionLine(line.getArticleId(), article.code(), article.label(),
                            article.unitCode(), line.getLotId(),
                            line.getLotId() != null ? lotNumbers.get(line.getLotId()) : null, line.getQuantity());
                })
                .sorted(Comparator.comparing(BottlingResponse.ConsumptionLine::articleLabel))
                .toList();

        int shortages = (int) needs.values().stream().filter(MaterialNeed::isShort).count();
        ArticleInfo finished = articles.get(bottling.getFinishedArticleId());
        return new BottlingResponse(
                bottling.getId(), bottling.getCode(), bottling.getStatus(), bottling.getLocation(),
                bottling.getContractor(), bottling.getStartDate(), bottling.getEndDate(),
                finished.id(), finished.code(), finished.label(),
                bottling.getPlannedBottles(), bottling.getBottleVolumeCl(), bottling.wineNeededHl(),
                bottling.getSourceTankId(), tank != null ? tank.code() : null,
                tank != null ? tank.volumeHl() : null, tank != null ? tank.wineLot() : null,
                bottling.getWineDescription(), bottling.getNotes(), materials,
                allocationViews(bottling.getAllocations()).getOrDefault(bottling.getId(), List.of()), shortages,
                isAlert(bottling, shortages),
                bottling.getProducedBottles(), bottling.getBrokenBottles(), bottling.getWineUsedHl(),
                bottling.getLotNumber(), consumptions, bottling.getClosedAt(), bottling.getClosedBy(),
                bottling.getCreatedBy(), bottling.getCreatedAt(), bottling.getUpdatedAt());
    }

    private static int shortageCount(List<MaterialNeed> needs) {
        return needs == null ? 0 : (int) needs.stream().filter(MaterialNeed::isShort).count();
    }

    /** Planifiée, avec des manquants, et elle commence dans moins de alertDays jours (ou a déjà commencé). */
    private boolean isAlert(Bottling bottling, int shortages) {
        return bottling.getStatus() == BottlingStatus.PLANNED && shortages > 0
                && !bottling.getStartDate().isAfter(LocalDate.now(ZONE).plusDays(alertDays));
    }

    /** MB-26-001, MB-26-002... : l'année de la date de début. */
    private String nextCode(LocalDate startDate) {
        String prefix = "MB-%02d-".formatted(startDate.getYear() % 100);
        int next = maxSuffix(bottlingRepository.findCodesLike(prefix + "%"), prefix) + 1;
        return prefix + "%03d".formatted(next);
    }

    /**
     * Le numéro de lot imprimé sur les bouteilles : L + année + quantième + n° de la mise ce jour-là.
     * La mise qui finit le 10 novembre 2026 (314e jour) : L26314-1. Règle générique, à adapter au client.
     */
    private String nextLotNumber(LocalDate endDate) {
        String prefix = "L%02d%03d-".formatted(endDate.getYear() % 100, endDate.getDayOfYear());
        int next = maxSuffix(bottlingRepository.findLotNumbersLike(prefix + "%"), prefix) + 1;
        return prefix + next;
    }

    private static int maxSuffix(List<String> values, String prefix) {
        return values.stream()
                .map(value -> value.substring(prefix.length()))
                .filter(suffix -> !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
    }

    private Bottling findBottling(UUID id) {
        return bottlingRepository.findWithMaterialsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mise en bouteille", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

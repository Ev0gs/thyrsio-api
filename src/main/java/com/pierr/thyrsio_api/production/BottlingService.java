package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.cuverie.CuverieService;
import com.pierr.thyrsio_api.cuverie.TankInfo;
import com.pierr.thyrsio_api.cuverie.TankLookup;
import com.pierr.thyrsio_api.production.BottlingPlanner.MaterialNeed;
import com.pierr.thyrsio_api.production.dto.*;
import com.pierr.thyrsio_api.referentiel.AppellationInfo;
import com.pierr.thyrsio_api.referentiel.AppellationLookup;
import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.referentiel.ArticleLookup;
import com.pierr.thyrsio_api.referentiel.ClientInfo;
import com.pierr.thyrsio_api.referentiel.ClientLookup;
import com.pierr.thyrsio_api.sales.OrderInfo;
import com.pierr.thyrsio_api.sales.OrderLookup;
import com.pierr.thyrsio_api.sales.OrderStatus;
import com.pierr.thyrsio_api.stock.LotService;
import com.pierr.thyrsio_api.stock.MovementType;
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
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Les mises en bouteille et leurs productions. Ce module orchestre les autres : à la clôture d'une production,
 * il consomme les matières sèches (Stock), tire le vin de la cuve (Cuverie) et fait entrer les bouteilles
 * en stock sous le numéro de lot de la production (Stock), dans une seule transaction :
 * si une étape échoue, rien n'est enregistré.
 */
@Service
public class BottlingService {

    /** Les dates du planning sont des dates françaises. */
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final BottlingRepository bottlingRepository;
    private final BottlingPlanner planner;
    private final LotNumberGenerator lotNumbers;
    private final ArticleLookup articleLookup;
    private final AppellationLookup appellationLookup;
    private final TankLookup tankLookup;
    private final StockService stockService;
    private final LotService lotService;
    private final CuverieService cuverieService;
    private final OrderLookup orderLookup;
    private final ClientLookup clientLookup;
    private final BottlingAllocationRepository allocationRepository;
    private final int alertDays;

    public BottlingService(BottlingRepository bottlingRepository,
                           BottlingPlanner planner,
                           LotNumberGenerator lotNumbers,
                           ArticleLookup articleLookup,
                           AppellationLookup appellationLookup,
                           TankLookup tankLookup,
                           StockService stockService,
                           LotService lotService,
                           CuverieService cuverieService,
                           OrderLookup orderLookup,
                           ClientLookup clientLookup,
                           BottlingAllocationRepository allocationRepository,
                           // Combien de jours avant une production on veut être alerté des manquants
                           @Value("${thyrsio.production.alert-days:10}") int alertDays) {
        this.bottlingRepository = bottlingRepository;
        this.planner = planner;
        this.lotNumbers = lotNumbers;
        this.articleLookup = articleLookup;
        this.appellationLookup = appellationLookup;
        this.tankLookup = tankLookup;
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
    public Page<BottlingResponse> list(LocalDate from, LocalDate to, BottlingStatus status, Pageable pageable) {
        List<Specification<Bottling>> filters = new ArrayList<>();
        if (from != null) filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), from));
        if (to != null) filters.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to));
        if (status != null) filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        Page<Bottling> page = bottlingRepository.findAll(Specification.allOf(filters), pageable);
        Map<UUID, BottlingResponse> responses = toResponses(page.getContent()).stream()
                .collect(Collectors.toMap(BottlingResponse::id, response -> response));
        return page.map(bottling -> responses.get(bottling.getId()));
    }

    @Transactional(readOnly = true)
    public BottlingResponse get(UUID id) {
        return toResponse(findBottling(id));
    }

    // ========== Planification ==========

    @Transactional
    public BottlingResponse create(BottlingRequest request, UUID userId) {
        if (request.productions().isEmpty()) {
            throw new IllegalArgumentException("Une mise réalise au moins une production");
        }
        LocalDate firstDay = request.productions().stream().map(ProductionRequest::startDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        Bottling bottling = new Bottling(nextCode(firstDay), userId);
        bottling.plan(request.location(), blankToNull(request.contractor()), blankToNull(request.notes()));
        for (ProductionRequest line : request.productions()) {
            if (line.id() != null) {
                throw new IllegalArgumentException("Une nouvelle mise n'a pas encore de production existante");
            }
            applyProduction(bottling, null, line);
        }
        // Enregistrée seulement une fois complète : ses dates viennent de ses productions
        bottling.refresh();
        bottlingRepository.saveAndFlush(bottling);
        return toResponse(bottling);
    }

    @Transactional
    public BottlingResponse update(UUID id, BottlingRequest request) {
        Bottling bottling = findBottling(id);
        bottling.plan(request.location(), blankToNull(request.contractor()), blankToNull(request.notes()));

        // Les productions planifiées absentes de la demande sont retirées (leur numéro de lot ne resservira pas)
        Set<UUID> kept = request.productions().stream().map(ProductionRequest::id)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        bottling.getProductions().stream()
                .filter(p -> p.getStatus() == ProductionStatus.PLANNED && !kept.contains(p.getId()))
                .toList()
                .forEach(bottling::removeProduction);

        for (ProductionRequest line : request.productions()) {
            BottlingProduction existing = line.id() != null ? bottling.production(line.id()) : null;
            if (existing != null) {
                existing.requirePlanned();
            }
            applyProduction(bottling, existing, line);
        }
        bottling.refresh();
        // Les nouvelles matières ne sont insérées qu'au flush : on le force pour calculer les manquants
        bottlingRepository.flush();
        return toResponse(bottling);
    }

    /** Annule ce qui reste à faire : les productions déjà clôturées restent. */
    @Transactional
    public BottlingResponse cancel(UUID id) {
        Bottling bottling = findBottling(id);
        bottling.cancel();
        return toResponse(bottling);
    }

    @Transactional
    public BottlingResponse cancelProduction(UUID id, UUID productionId) {
        Bottling bottling = findBottling(id);
        bottling.requireOpen();
        bottling.production(productionId).cancel();
        bottling.refresh();
        return toResponse(bottling);
    }

    // ========== Clôture d'une production ==========

    @Transactional
    public BottlingResponse closeProduction(UUID id, UUID productionId, CloseProductionRequest request, UUID userId) {
        Bottling bottling = findBottling(id);
        bottling.requireOpen();
        BottlingProduction production = bottling.production(productionId);
        production.requirePlanned();
        if (production.getStartDate().isAfter(LocalDate.now(ZONE))) {
            throw new BusinessRuleException("La production " + production.getLotNumber() + " commence le "
                    + production.getStartDate() + " : elle ne peut pas être clôturée avant");
        }
        if (production.getLotNumber() == null) {
            throw new BusinessRuleException("La production n'a pas encore de numéro de lot : "
                    + "choisissez son appellation pour le lui attribuer");
        }
        if (bottling.getLocation() == BottlingLocation.ON_SITE && request.wineUsedHl() == null) {
            throw new IllegalArgumentException("Indiquez le volume de vin tiré de la cuve");
        }
        if (bottling.getLocation() == BottlingLocation.AT_PRODUCER && request.wineUsedHl() != null) {
            throw new IllegalArgumentException("Mise chez le producteur : le vin ne sort pas de nos cuves");
        }
        ArticleInfo finished = articleLookup.findById(production.getFinishedArticleId()).orElseThrow();

        // La fin de la production (midi et demi le matin, fin d'après-midi sinon), jamais dans le futur
        Instant end = production.getEndDate().atTime(production.getSlot().end()).atZone(ZONE).toInstant();
        Instant occurredAt = end.isBefore(Instant.now()) ? end : Instant.now();
        String reference = "Mise " + bottling.getCode() + " · lot " + production.getLotNumber();

        // 1. Les matières sèches consommées, une ligne par article, dans un ordre fixe pour éviter les interblocages
        List<ConsumptionRequest> consumptions = request.consumptions().stream()
                .sorted(Comparator.comparing(ConsumptionRequest::articleId))
                .toList();
        if (consumptions.stream().map(ConsumptionRequest::articleId).distinct().count() != consumptions.size()) {
            throw new IllegalArgumentException("Une matière sèche ne figure qu'une fois dans les consommations");
        }
        List<BottlingProduction.Consumed> consumed = new ArrayList<>();
        for (ConsumptionRequest line : consumptions) {
            ArticleInfo article = articleLookup.findById(line.articleId())
                    .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + line.articleId()));
            if (article.finishedGood()) {
                throw new IllegalArgumentException(article.code() + " est un produit fini, pas une matière sèche");
            }
            // StockService vérifie les décimales et le stock disponible (409 si insuffisant)
            stockService.record(new StockMovementCommand(line.articleId(), null, MovementType.CONSUMPTION,
                    line.quantity(), occurredAt, reference, null, userId));
            consumed.add(new BottlingProduction.Consumed(line.articleId(), line.quantity()));
        }

        // 2. Le vin tiré de la cuve
        if (bottling.getLocation() == BottlingLocation.ON_SITE) {
            cuverieService.recordBottling(production.getSourceTankId(), request.wineUsedHl(), occurredAt,
                    reference, userId);
        }

        // 3. Les bouteilles produites entrent en stock, sous le numéro de lot attribué à la planification
        UUID lotId = lotService.create(new CreateLotRequest(finished.id(), production.getLotNumber(), null,
                "Mise " + bottling.getCode())).id();
        stockService.record(new StockMovementCommand(finished.id(), lotId, MovementType.PRODUCTION,
                BigDecimal.valueOf(request.producedBottles()), occurredAt, reference, null, userId));

        production.close(request.producedBottles(),
                request.brokenBottles() != null ? request.brokenBottles() : 0,
                request.wineUsedHl(), consumed, userId);
        bottling.refresh();
        bottlingRepository.flush();
        return toResponse(bottling);
    }

    // ========== Méthodes utilitaires ==========

    /**
     * Vérifie une production demandée, puis l'applique : à une production existante (planifiée), ou à une
     * nouvelle, créée seulement une fois tout vérifié (une production incomplète ne doit jamais partir en base).
     * Lui attribue un numéro de lot si elle n'en a pas, ou si son appellation ou son millésime ont changé.
     */
    private void applyProduction(Bottling bottling, BottlingProduction existing, ProductionRequest request) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate() != null ? request.endDate() : start;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("La date de fin d'une production est antérieure à sa date de début");
        }
        ProductionSlot slot = request.slot() != null ? request.slot() : ProductionSlot.FULL_DAY;

        ArticleInfo finished = articleLookup.findById(request.finishedArticleId())
                .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + request.finishedArticleId()));
        if (!finished.finishedGood()) {
            throw new IllegalArgumentException(finished.code() + " n'est pas un produit fini (catégorie "
                    + ArticleInfo.FINISHED_GOODS_CATEGORY + ")");
        }
        if (!finished.active() && (existing == null || !finished.id().equals(existing.getFinishedArticleId()))) {
            throw new BusinessRuleException("Le produit " + finished.code() + " est archivé");
        }

        UUID tankId = null;
        if (bottling.getLocation() == BottlingLocation.ON_SITE) {
            if (request.sourceTankId() == null) {
                throw new IllegalArgumentException("Une mise chez nous tire son vin d'une cuve : choisissez-la pour "
                        + finished.code());
            }
            TankInfo tank = tankLookup.findById(request.sourceTankId())
                    .orElseThrow(() -> new IllegalArgumentException("Cuve inconnue : " + request.sourceTankId()));
            if (!tank.active()) {
                throw new BusinessRuleException("La cuve " + tank.code() + " est archivée");
            }
            tankId = tank.id();
        }

        AppellationInfo appellation = appellationLookup.findById(request.appellationId())
                .orElseThrow(() -> new IllegalArgumentException("Appellation inconnue : " + request.appellationId()));
        if (!appellation.active() && (existing == null || !appellation.id().equals(existing.getAppellationId()))) {
            throw new BusinessRuleException("L'appellation " + appellation.name() + " est archivée");
        }

        Map<UUID, BigDecimal> quantities = checkMaterials(request.materials());

        BottlingProduction production = existing != null ? existing : bottling.addProduction();
        production.plan(start, end, slot, finished.id(), request.plannedBottles(), request.bottleVolumeCl(), tankId,
                bottling.getLocation() == BottlingLocation.AT_PRODUCER ? blankToNull(request.wineDescription()) : null,
                appellation.id(), request.vintage());
        production.replaceMaterials(quantities);
        production.replaceAllocations(checkAllocations(production, finished, request));

        String prefix = LotNumberGenerator.prefix(appellation, request.vintage());
        if (production.needsLotNumber(prefix)) {
            production.assignLotNumber(lotNumbers.next(appellation, request.vintage()));
        }
    }

    /** Les matières : un article une seule fois, une matière sèche, les décimales de son unité. */
    private Map<UUID, BigDecimal> checkMaterials(List<MaterialRequest> requested) {
        List<MaterialRequest> lines = requested != null ? requested : List.of();
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
        return quantities;
    }

    /**
     * Les commandes servies : chaque ligne porte sur le produit de la production, attend encore assez de
     * bouteilles (en tenant compte des autres productions planifiées pour elle), et le total ne dépasse pas
     * la production.
     */
    private Map<UUID, Integer> checkAllocations(BottlingProduction production, ArticleInfo finished,
                                                ProductionRequest request) {
        List<AllocationRequest> requested = request.allocations() != null ? request.allocations() : List.of();
        if (requested.isEmpty()) {
            return Map.of();
        }
        Set<UUID> lineIds = requested.stream().map(AllocationRequest::orderLineId).collect(Collectors.toSet());
        Map<UUID, OrderInfo.Line> lines = orderLookup.findLines(lineIds);
        // Ce que les AUTRES productions planifiées réservent déjà à ces lignes
        Map<UUID, Integer> elsewhere = new HashMap<>();
        allocationRepository.findForOrderLines(lineIds, ProductionStatus.CANCELLED).stream()
                .filter(a -> a.getProduction().getStatus() == ProductionStatus.PLANNED)
                .filter(a -> a.getProduction() != production)
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
                        + " porte sur un autre produit que celui de la production");
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
            throw new IllegalArgumentException("Les commandes servies (" + total
                    + " bouteilles) dépassent la production (" + request.plannedBottles() + ")");
        }
        return bottles;
    }

    private BottlingResponse toResponse(Bottling bottling) {
        return toResponses(List.of(bottling)).getFirst();
    }

    /** Les réponses de plusieurs mises, avec une requête par référentiel quelle que soit leur quantité. */
    private List<BottlingResponse> toResponses(List<Bottling> bottlings) {
        List<BottlingProduction> productions = bottlings.stream()
                .flatMap(bottling -> bottling.getProductions().stream()).toList();
        boolean anyPlanned = productions.stream().anyMatch(p -> p.getStatus() == ProductionStatus.PLANNED);
        Map<UUID, List<MaterialNeed>> needs = anyPlanned ? planner.plan() : Map.of();

        Set<UUID> articleIds = new HashSet<>();
        productions.forEach(p -> {
            articleIds.add(p.getFinishedArticleId());
            p.getMaterials().forEach(material -> articleIds.add(material.getArticleId()));
            p.getConsumptions().forEach(line -> articleIds.add(line.getArticleId()));
        });
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(articleIds);
        Map<UUID, TankInfo> tanks = tankLookup.findAllById(productions.stream()
                .map(BottlingProduction::getSourceTankId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, AppellationInfo> appellations = appellationLookup.findAllById(productions.stream()
                .map(BottlingProduction::getAppellationId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<BottlingAllocation, AllocationLine> allocations = allocationViews(productions.stream()
                .flatMap(p -> p.getAllocations().stream()).toList());

        return bottlings.stream().map(bottling -> {
            List<ProductionResponse> lines = bottling.getProductions().stream()
                    .sorted(BottlingPlanner.CHRONOLOGICAL)
                    .map(p -> toProduction(p, needs.get(p.getId()), articles, tanks, appellations, allocations))
                    .toList();
            return new BottlingResponse(
                    bottling.getId(), bottling.getCode(), bottling.getStatus(), bottling.getLocation(),
                    bottling.getContractor(), bottling.getStartDate(), bottling.getEndDate(), bottling.getNotes(),
                    lines.stream().filter(p -> p.status() != ProductionStatus.CANCELLED)
                            .mapToInt(ProductionResponse::plannedBottles).sum(),
                    lines.stream().map(ProductionResponse::producedBottles).filter(Objects::nonNull)
                            .mapToInt(Integer::intValue).sum(),
                    lines.stream().mapToInt(ProductionResponse::shortageCount).sum(),
                    lines.stream().anyMatch(ProductionResponse::alert),
                    lines, bottling.getCreatedBy(), bottling.getCreatedAt(), bottling.getUpdatedAt());
        }).toList();
    }

    private ProductionResponse toProduction(BottlingProduction production, List<MaterialNeed> needList,
                                            Map<UUID, ArticleInfo> articles, Map<UUID, TankInfo> tanks,
                                            Map<UUID, AppellationInfo> appellations,
                                            Map<BottlingAllocation, AllocationLine> allocations) {
        Map<UUID, MaterialNeed> needs = needList == null ? Map.of() : needList.stream()
                .collect(Collectors.toMap(MaterialNeed::articleId, need -> need));
        List<ProductionResponse.MaterialLine> materials = production.getMaterials().stream()
                .map(material -> {
                    ArticleInfo article = articles.get(material.getArticleId());
                    MaterialNeed need = needs.get(material.getArticleId());
                    return new ProductionResponse.MaterialLine(
                            material.getArticleId(), article.code(), article.label(), article.categoryLabel(),
                            article.unitCode(), material.getPlannedQuantity(),
                            need != null ? need.stock() : null,
                            need != null ? need.reservedBefore() : null,
                            need != null ? need.shortage() : null);
                })
                .sorted(Comparator.comparing(ProductionResponse.MaterialLine::categoryLabel)
                        .thenComparing(ProductionResponse.MaterialLine::articleLabel))
                .toList();
        List<ProductionResponse.ConsumptionLine> consumptions = production.getConsumptions().stream()
                .map(line -> {
                    ArticleInfo article = articles.get(line.getArticleId());
                    return new ProductionResponse.ConsumptionLine(line.getArticleId(), article.code(),
                            article.label(), article.unitCode(), line.getQuantity());
                })
                .sorted(Comparator.comparing(ProductionResponse.ConsumptionLine::articleLabel))
                .toList();

        int shortages = (int) needs.values().stream().filter(MaterialNeed::isShort).count();
        boolean alert = production.getStatus() == ProductionStatus.PLANNED && shortages > 0
                && !production.getStartDate().isAfter(LocalDate.now(ZONE).plusDays(alertDays));
        ArticleInfo finished = articles.get(production.getFinishedArticleId());
        TankInfo tank = production.getSourceTankId() != null ? tanks.get(production.getSourceTankId()) : null;
        AppellationInfo appellation = production.getAppellationId() != null
                ? appellations.get(production.getAppellationId()) : null;
        return new ProductionResponse(
                production.getId(), production.getStatus(), production.getStartDate(), production.getEndDate(),
                production.getSlot(), finished.id(), finished.code(), finished.label(),
                production.getPlannedBottles(), production.getBottleVolumeCl(), production.wineNeededHl(),
                production.getSourceTankId(), tank != null ? tank.code() : null,
                tank != null ? tank.volumeHl() : null, tank != null ? tank.wineLot() : null,
                production.getWineDescription(),
                appellation != null ? appellation.id() : null, appellation != null ? appellation.code() : null,
                appellation != null ? appellation.name() : null, production.getVintage(),
                production.getLotNumber(), materials,
                production.getAllocations().stream().map(allocations::get).toList(),
                shortages, alert,
                production.getProducedBottles(), production.getBrokenBottles(), production.getWineUsedHl(),
                consumptions, production.getClosedAt(), production.getClosedBy());
    }

    /** Chaque allocation avec le code de sa commande et le nom du client. */
    private Map<BottlingAllocation, AllocationLine> allocationViews(Collection<BottlingAllocation> allocations) {
        if (allocations.isEmpty()) {
            return Map.of();
        }
        Map<UUID, OrderInfo.Line> lines = orderLookup.findLines(
                allocations.stream().map(BottlingAllocation::getOrderLineId).collect(Collectors.toSet()));
        Map<UUID, ClientInfo> clients = clientLookup.findAllById(
                lines.values().stream().map(OrderInfo.Line::clientId).collect(Collectors.toSet()));
        Map<BottlingAllocation, AllocationLine> views = new IdentityHashMap<>();
        for (BottlingAllocation allocation : allocations) {
            OrderInfo.Line line = lines.get(allocation.getOrderLineId());
            ClientInfo client = line != null ? clients.get(line.clientId()) : null;
            views.put(allocation, new AllocationLine(allocation.getOrderLineId(), line != null ? line.orderId() : null,
                    line != null ? line.orderCode() : null, client != null ? client.name() : null,
                    allocation.getQuantity()));
        }
        return views;
    }

    /** MB-26-001, MB-26-002... : l'année du premier jour de la mise. */
    private String nextCode(LocalDate startDate) {
        String prefix = "MB-%02d-".formatted(startDate.getYear() % 100);
        int next = bottlingRepository.findCodesLike(prefix + "%").stream()
                .map(value -> value.substring(prefix.length()))
                .filter(suffix -> !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
        return prefix + "%03d".formatted(next);
    }

    private Bottling findBottling(UUID id) {
        return bottlingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mise en bouteille", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

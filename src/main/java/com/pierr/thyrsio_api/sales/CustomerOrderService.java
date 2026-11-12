package com.pierr.thyrsio_api.sales;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.*;
import com.pierr.thyrsio_api.sales.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerOrderService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final CustomerOrderRepository orderRepository;
    private final CustomerOrderLineRepository lineRepository;
    private final OrderCoverage coverage;
    private final PlannedSupply plannedSupply;
    private final OrderShipments orderShipments;
    private final ClientLookup clientLookup;
    private final ArticleLookup articleLookup;

    public CustomerOrderService(CustomerOrderRepository orderRepository,
                                CustomerOrderLineRepository lineRepository,
                                OrderCoverage coverage,
                                PlannedSupply plannedSupply,
                                OrderShipments orderShipments,
                                ClientLookup clientLookup,
                                ArticleLookup articleLookup) {
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
        this.coverage = coverage;
        this.plannedSupply = plannedSupply;
        this.orderShipments = orderShipments;
        this.clientLookup = clientLookup;
        this.articleLookup = articleLookup;
    }

    // ========== Lecture ==========

    /**
     * La liste, filtrable : statut, client, commandes à charger au plus tard tel jour (loadingTo),
     * recherche dans le code et la référence client (q).
     */
    @Transactional(readOnly = true)
    public Page<OrderRow> list(OrderStatus status, boolean openOnly, UUID clientId, LocalDate loadingTo,
                               String search, Pageable pageable) {
        List<Specification<CustomerOrder>> filters = new ArrayList<>();
        if (status != null) filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        if (openOnly) filters.add((root, query, cb) ->
                root.get("status").in(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_SHIPPED));
        if (clientId != null) filters.add((root, query, cb) -> cb.equal(root.get("clientId"), clientId));
        if (loadingTo != null) filters.add((root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("requestedLoadingDate"), loadingTo));
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            filters.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("customerReference")), pattern)));
        }
        Page<CustomerOrder> page = orderRepository.findAll(Specification.allOf(filters), pageable);

        // Les lignes de la page en une requête, puis les clients en une autre
        Map<UUID, CustomerOrder> withLines = orderRepository.findWithLinesByIdIn(
                        page.stream().map(CustomerOrder::getId).toList()).stream()
                .collect(Collectors.toMap(CustomerOrder::getId, Function.identity()));
        Map<UUID, ClientInfo> clients = clientLookup.findAllById(
                page.stream().map(CustomerOrder::getClientId).collect(Collectors.toSet()));
        Map<UUID, OrderCoverage.Coverage> covered = page.stream().anyMatch(o -> o.getStatus().isOpen())
                ? coverage.compute() : Map.of();

        return page.map(summary -> {
            CustomerOrder order = withLines.getOrDefault(summary.getId(), summary);
            ClientInfo client = clients.get(order.getClientId());
            List<CustomerOrderLine> lines = order.getLines();
            int missing = lines.stream()
                    .map(line -> covered.get(line.getId()))
                    .filter(Objects::nonNull)
                    .mapToInt(OrderCoverage.Coverage::missing).sum();
            return new OrderRow(order.getId(), order.getCode(), order.getStatus(), order.getClientId(),
                    client != null ? client.name() : null, client != null ? client.countryCode() : null,
                    order.getCustomerReference(), order.getOrderDate(), order.getRequestedLoadingDate(),
                    lines.size(), lines.stream().mapToInt(CustomerOrderLine::getQuantity).sum(),
                    lines.stream().mapToInt(CustomerOrderLine::getShippedQuantity).sum(), missing);
        });
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return toResponse(findOrder(id));
    }

    /** Les lignes confirmées qui ne sont couvertes ni par le stock ni par une mise, de la plus urgente à la moins urgente. */
    @Transactional(readOnly = true)
    public List<LineToPlan> linesToPlan() {
        Map<UUID, OrderCoverage.Coverage> covered = coverage.compute();
        List<UUID> lineIds = covered.entrySet().stream()
                .filter(entry -> entry.getValue().missing() > 0)
                .map(Map.Entry::getKey)
                .toList();
        if (lineIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, OrderInfo.Line> lines = lineRepository.findWithOrderByIdIn(lineIds).stream()
                .map(OrderInfo::lineOf)
                .collect(Collectors.toMap(OrderInfo.Line::id, Function.identity()));
        Map<UUID, ClientInfo> clients = clientLookup.findAllById(
                lines.values().stream().map(OrderInfo.Line::clientId).collect(Collectors.toSet()));
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                lines.values().stream().map(OrderInfo.Line::articleId).collect(Collectors.toSet()));

        return lineIds.stream()
                .map(lineId -> {
                    OrderInfo.Line line = lines.get(lineId);
                    OrderCoverage.Coverage c = covered.get(lineId);
                    ClientInfo client = clients.get(line.clientId());
                    ArticleInfo article = articles.get(line.articleId());
                    return new LineToPlan(lineId, line.orderId(), line.orderCode(),
                            client != null ? client.name() : null, line.articleId(),
                            article != null ? article.code() : null, article != null ? article.label() : null,
                            line.requestedLoadingDate(), c.remaining(), c.fromStock(), c.fromBottlings(), c.missing());
                })
                .sorted(Comparator.comparing(LineToPlan::requestedLoadingDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(LineToPlan::orderCode))
                .toList();
    }

    // ========== Écriture ==========

    @Transactional
    public OrderResponse create(CreateOrderRequest request, UUID userId) {
        ClientInfo client = clientLookup.findById(request.clientId())
                .orElseThrow(() -> new IllegalArgumentException("Client inconnu : " + request.clientId()));
        if (!client.active()) {
            throw new BusinessRuleException("Le client " + client.name() + " est archivé");
        }
        LocalDate orderDate = request.orderDate() != null ? request.orderDate() : LocalDate.now(ZONE);
        CustomerOrder order = new CustomerOrder(nextCode(orderDate), client.id(), userId);
        UUID addressId = request.deliveryAddressId() != null
                ? checkAddress(client, request.deliveryAddressId())
                : clientLookup.findDefaultAddress(client.id()).map(DeliveryAddressInfo::id).orElse(null);
        order.describe(addressId, blankToNull(request.customerReference()), orderDate,
                request.requestedLoadingDate(),
                request.incoterm() != null ? request.incoterm() : client.defaultIncoterm(),
                blankToNull(request.notes()));
        order.replaceLines(lineValues(request.lines()));
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse update(UUID id, UpdateOrderRequest request) {
        CustomerOrder order = findOrder(id);
        order.requireEditable();
        if (orderShipments.hasDraft(order.getId())) {
            throw new BusinessRuleException("Une expédition de la commande " + order.getCode()
                    + " est en préparation : annulez-la avant de modifier la commande");
        }
        ClientInfo client = clientLookup.findById(order.getClientId()).orElseThrow();
        UUID addressId = request.deliveryAddressId() != null ? checkAddress(client, request.deliveryAddressId()) : null;

        Map<UUID, CustomerOrder.LineValues> values = lineValues(request.lines());
        // Une ligne retirée alors qu'une mise produit pour elle : on refuse, avec un message clair
        List<UUID> removed = order.getLines().stream()
                .filter(line -> !values.containsKey(line.getArticleId()))
                .map(CustomerOrderLine::getId)
                .toList();
        if (!removed.isEmpty()) {
            plannedSupply.allocationsFor(removed).stream().findFirst().ifPresent(allocation -> {
                throw new BusinessRuleException("La mise " + allocation.bottlingCode()
                        + " produit pour une ligne retirée : retirez d'abord la commande de cette mise");
            });
        }
        order.describe(addressId, blankToNull(request.customerReference()), request.orderDate(),
                request.requestedLoadingDate(), request.incoterm(), blankToNull(request.notes()));
        order.replaceLines(values);
        orderRepository.flush();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse confirm(UUID id) {
        CustomerOrder order = findOrder(id);
        order.confirm();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        CustomerOrder order = findOrder(id);
        if (orderShipments.hasDraft(order.getId())) {
            throw new BusinessRuleException("Une expédition de la commande " + order.getCode()
                    + " est en préparation : annulez-la d'abord");
        }
        order.cancel();
        return toResponse(order);
    }

    /**
     * Appelé par le module Expéditions quand un chargement part, dans sa transaction.
     * Verrouillage optimiste : deux expéditions simultanées de la même commande ne peuvent pas
     * expédier deux fois les mêmes bouteilles (la seconde reçoit un 409).
     */
    @Transactional
    public void recordShipped(UUID orderId, Map<UUID, Integer> bottlesByLine) {
        findOrder(orderId).recordShipped(bottlesByLine);
        orderRepository.flush();
    }

    // ========== Méthodes utilitaires ==========

    private Map<UUID, CustomerOrder.LineValues> lineValues(List<OrderLineRequest> requested) {
        List<OrderLineRequest> lines = requested != null ? requested : List.of();
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                lines.stream().map(OrderLineRequest::articleId).collect(Collectors.toSet()));
        Map<UUID, CustomerOrder.LineValues> values = new LinkedHashMap<>();
        for (OrderLineRequest line : lines) {
            ArticleInfo article = articles.get(line.articleId());
            if (article == null) {
                throw new IllegalArgumentException("Article inconnu : " + line.articleId());
            }
            if (!article.finishedGood()) {
                throw new IllegalArgumentException(article.code() + " n'est pas un produit fini : on ne commande que des bouteilles");
            }
            if (values.put(article.id(), new CustomerOrder.LineValues(line.quantity(), line.unitPrice())) != null) {
                throw new IllegalArgumentException("Le produit " + article.code() + " figure deux fois dans la commande");
            }
        }
        return values;
    }

    private UUID checkAddress(ClientInfo client, UUID addressId) {
        DeliveryAddressInfo address = clientLookup.findAddress(client.id(), addressId)
                .orElseThrow(() -> new IllegalArgumentException("Cette adresse n'appartient pas au client " + client.name()));
        return address.id();
    }

    private OrderResponse toResponse(CustomerOrder order) {
        ClientInfo client = clientLookup.findById(order.getClientId()).orElseThrow();
        DeliveryAddressInfo address = order.getDeliveryAddressId() != null
                ? clientLookup.findAddress(order.getClientId(), order.getDeliveryAddressId()).orElse(null)
                : null;
        List<CustomerOrderLine> lines = order.getLines();
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                lines.stream().map(CustomerOrderLine::getArticleId).collect(Collectors.toSet()));
        Map<UUID, List<PlannedSupply.Allocation>> allocations = plannedSupply
                .allocationsFor(lines.stream().map(CustomerOrderLine::getId).toList()).stream()
                .collect(Collectors.groupingBy(PlannedSupply.Allocation::orderLineId));
        Map<UUID, OrderCoverage.Coverage> covered = order.getStatus().isOpen() ? coverage.compute() : Map.of();

        List<OrderResponse.Line> lineViews = lines.stream()
                .map(line -> {
                    ArticleInfo article = articles.get(line.getArticleId());
                    OrderCoverage.Coverage c = covered.get(line.getId());
                    List<OrderResponse.Allocation> lineAllocations = allocations.getOrDefault(line.getId(), List.of())
                            .stream()
                            .map(a -> new OrderResponse.Allocation(a.bottlingId(), a.bottlingCode(), a.startDate(),
                                    a.bottlingStatus(), a.quantity()))
                            .sorted(Comparator.comparing(OrderResponse.Allocation::startDate))
                            .toList();
                    return new OrderResponse.Line(line.getId(), line.getArticleId(),
                            article != null ? article.code() : null, article != null ? article.label() : null,
                            line.getQuantity(), line.getUnitPrice(), line.getShippedQuantity(), line.remaining(),
                            c != null ? c.fromStock() : null, c != null ? c.fromBottlings() : null,
                            c != null ? c.missing() : null, lineAllocations);
                })
                .sorted(Comparator.comparing(OrderResponse.Line::articleLabel, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal total = lines.stream().anyMatch(line -> line.getUnitPrice() != null)
                ? lines.stream()
                .filter(line -> line.getUnitPrice() != null)
                .map(line -> line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;

        return new OrderResponse(order.getId(), order.getCode(), order.getStatus(), client.id(), client.code(),
                client.name(), client.countryCode(), order.getDeliveryAddressId(), address,
                order.getCustomerReference(), order.getOrderDate(), order.getRequestedLoadingDate(),
                order.getIncoterm(), order.getNotes(), lineViews,
                lines.stream().mapToInt(CustomerOrderLine::getQuantity).sum(),
                lines.stream().mapToInt(CustomerOrderLine::getShippedQuantity).sum(), total,
                order.getId() != null ? orderShipments.forOrder(order.getId()) : List.of(),
                order.getConfirmedAt(), order.getCreatedBy(), order.getCreatedAt(), order.getUpdatedAt());
    }

    /** CC-26-001 : l'année de la date de commande. */
    private String nextCode(LocalDate orderDate) {
        String prefix = "CC-%02d-".formatted(orderDate.getYear() % 100);
        int max = orderRepository.findCodesLike(prefix + "%").stream()
                .map(code -> code.substring(prefix.length()))
                .filter(suffix -> !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return prefix + "%03d".formatted(max + 1);
    }

    private CustomerOrder findOrder(UUID id) {
        return orderRepository.findWithLinesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

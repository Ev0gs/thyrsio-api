package com.pierr.thyrsio_api.shipping;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.*;
import com.pierr.thyrsio_api.sales.CustomerOrderService;
import com.pierr.thyrsio_api.sales.OrderInfo;
import com.pierr.thyrsio_api.sales.OrderLookup;
import com.pierr.thyrsio_api.shipping.dto.*;
import com.pierr.thyrsio_api.stock.LotService;
import com.pierr.thyrsio_api.stock.MovementType;
import com.pierr.thyrsio_api.stock.StockLookup;
import com.pierr.thyrsio_api.stock.StockMovementCommand;
import com.pierr.thyrsio_api.stock.StockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Les expéditions. Au départ, dans une seule transaction : les bouteilles sortent du stock lot par lot
 * (mouvements SHIPMENT), la commande avance (expédiée en partie ou en totalité), le bon de livraison se fige.
 */
@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderLookup orderLookup;
    private final CustomerOrderService orderService;
    private final ClientLookup clientLookup;
    private final ArticleLookup articleLookup;
    private final StockLookup stockLookup;
    private final StockService stockService;
    private final LotService lotService;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           OrderLookup orderLookup,
                           CustomerOrderService orderService,
                           ClientLookup clientLookup,
                           ArticleLookup articleLookup,
                           StockLookup stockLookup,
                           StockService stockService,
                           LotService lotService) {
        this.shipmentRepository = shipmentRepository;
        this.orderLookup = orderLookup;
        this.orderService = orderService;
        this.clientLookup = clientLookup;
        this.articleLookup = articleLookup;
        this.stockLookup = stockLookup;
        this.stockService = stockService;
        this.lotService = lotService;
    }

    // ========== Lecture ==========

    @Transactional(readOnly = true)
    public Page<ShipmentRow> list(ShipmentStatus status, UUID orderId, LocalDate from, LocalDate to, Pageable pageable) {
        List<Specification<Shipment>> filters = new ArrayList<>();
        if (status != null) filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        if (orderId != null) filters.add((root, query, cb) -> cb.equal(root.get("orderId"), orderId));
        if (from != null) filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("plannedDate"), from));
        if (to != null) filters.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("plannedDate"), to));
        Page<Shipment> page = shipmentRepository.findAll(Specification.allOf(filters), pageable);

        Map<UUID, Shipment> withLines = shipmentRepository.findWithLinesByIdIn(
                        page.stream().map(Shipment::getId).toList()).stream()
                .collect(Collectors.toMap(Shipment::getId, Function.identity()));
        Map<UUID, OrderInfo> orders = orderLookup.findOrders(
                page.stream().map(Shipment::getOrderId).collect(Collectors.toSet()));
        Map<UUID, ClientInfo> clients = clientLookup.findAllById(
                orders.values().stream().map(OrderInfo::clientId).collect(Collectors.toSet()));

        return page.map(summary -> {
            Shipment shipment = withLines.getOrDefault(summary.getId(), summary);
            OrderInfo order = orders.get(shipment.getOrderId());
            ClientInfo client = order != null ? clients.get(order.clientId()) : null;
            return new ShipmentRow(shipment.getId(), shipment.getCode(), shipment.getStatus(), shipment.getOrderId(),
                    order != null ? order.code() : null, client != null ? client.name() : null,
                    client != null ? client.countryCode() : null, shipment.getPlannedDate(), shipment.getShippedAt(),
                    shipment.getCarrier(), shipment.totalBottles());
        });
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(UUID id) {
        return toResponse(findShipment(id));
    }

    // ========== Préparation ==========

    @Transactional
    public ShipmentResponse create(ShipmentRequest request, UUID userId) {
        if (request.orderId() == null) {
            throw new IllegalArgumentException("Précisez la commande à expédier");
        }
        OrderInfo order = openOrder(request.orderId());
        Shipment shipment = new Shipment(nextCode(request.plannedDate()), order.id(), userId);
        shipment.describe(request.plannedDate(), blankToNull(request.carrier()),
                blankToNull(request.transportReference()), blankToNull(request.notes()));
        shipment.addLines(checkLines(order, request.lines()));
        shipmentRepository.save(shipment);
        return toResponse(shipment);
    }

    @Transactional
    public ShipmentResponse update(UUID id, ShipmentRequest request) {
        Shipment shipment = findShipment(id);
        shipment.requireDraft();
        OrderInfo order = openOrder(shipment.getOrderId());
        List<Shipment.LineValues> lines = checkLines(order, request.lines());
        shipment.describe(request.plannedDate(), blankToNull(request.carrier()),
                blankToNull(request.transportReference()), blankToNull(request.notes()));
        shipment.clearLines();
        shipmentRepository.flush();  // les suppressions d'abord, puis les nouvelles lignes
        shipment.addLines(lines);
        shipmentRepository.flush();
        return toResponse(shipment);
    }

    @Transactional
    public ShipmentResponse cancel(UUID id) {
        Shipment shipment = findShipment(id);
        shipment.cancel();
        return toResponse(shipment);
    }

    // ========== Départ ==========

    @Transactional
    public ShipmentResponse ship(UUID id, UUID userId) {
        Shipment shipment = findShipment(id);
        shipment.requireDraft();
        OrderInfo order = openOrder(shipment.getOrderId());
        Instant now = Instant.now();
        String reference = "Expédition " + shipment.getCode() + " · " + order.code();

        // 1. Les bouteilles sortent du stock, dans un ordre fixe (article puis lot) pour éviter les interblocages.
        //    StockService refuse (409) si un lot n'a pas assez de bouteilles : rien n'est alors enregistré.
        shipment.getLines().stream()
                .sorted(Comparator.comparing(ShipmentLine::getArticleId).thenComparing(ShipmentLine::getLotId))
                .forEach(line -> stockService.record(new StockMovementCommand(line.getArticleId(), line.getLotId(),
                        MovementType.SHIPMENT, BigDecimal.valueOf(line.getQuantity()), now, reference, null, userId)));

        // 2. La commande avance (et refuse si on expédie plus que ce qui reste à expédier)
        Map<UUID, Integer> bottlesByLine = new HashMap<>();
        shipment.getLines().forEach(line -> bottlesByLine.merge(line.getOrderLineId(), line.getQuantity(), Integer::sum));
        orderService.recordShipped(order.id(), bottlesByLine);

        // 3. Le bon de livraison se fige, avec l'adresse telle qu'elle est aujourd'hui
        ClientInfo client = clientLookup.findById(order.clientId()).orElseThrow();
        shipment.markShipped(deliveryAddressText(order, client), userId);
        return toResponse(shipment);
    }

    // ========== Méthodes utilitaires ==========

    private OrderInfo openOrder(UUID orderId) {
        OrderInfo order = orderLookup.findOrder(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Commande inconnue : " + orderId));
        if (!order.status().isOpen()) {
            throw new BusinessRuleException("La commande " + order.code() + " n'est pas à expédier (" + order.status()
                    + ") : elle doit être confirmée, et pas encore entièrement expédiée");
        }
        return order;
    }

    /** Chaque ligne appartient à la commande, son lot est un lot du bon produit, et on ne charge pas plus que le reste dû. */
    private List<Shipment.LineValues> checkLines(OrderInfo order, List<ShipmentLineRequest> requested) {
        Map<UUID, OrderInfo.Line> orderLines = order.lines().stream()
                .collect(Collectors.toMap(OrderInfo.Line::id, Function.identity()));
        Map<UUID, Integer> loaded = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<Shipment.LineValues> values = new ArrayList<>();
        for (ShipmentLineRequest request : requested) {
            OrderInfo.Line line = orderLines.get(request.orderLineId());
            if (line == null) {
                throw new IllegalArgumentException("Cette ligne n'appartient pas à la commande " + order.code());
            }
            UUID lotArticle;
            try {
                lotArticle = lotService.get(request.lotId()).articleId();
            } catch (ResourceNotFoundException unknown) {
                throw new IllegalArgumentException("Lot inconnu : " + request.lotId());
            }
            if (!lotArticle.equals(line.articleId())) {
                throw new IllegalArgumentException("Ce lot n'est pas un lot du produit commandé");
            }
            if (!seen.add(request.orderLineId() + "/" + request.lotId())) {
                throw new IllegalArgumentException("Le même lot figure deux fois pour la même ligne");
            }
            int total = loaded.merge(line.id(), request.quantity(), Integer::sum);
            if (total > line.remaining()) {
                throw new BusinessRuleException("La commande " + order.code() + " n'attend plus que "
                        + line.remaining() + " bouteilles de ce produit");
            }
            values.add(new Shipment.LineValues(line.id(), line.articleId(), request.lotId(), request.quantity()));
        }
        return values;
    }

    private String deliveryAddressText(OrderInfo order, ClientInfo client) {
        if (order.deliveryAddressId() != null) {
            Optional<DeliveryAddressInfo> address = clientLookup.findAddress(client.id(), order.deliveryAddressId());
            if (address.isPresent()) {
                return address.get().toPostalText(client.name());
            }
        }
        // Pas d'adresse de livraison : celle du siège du client
        return client.name() + "\n" + clientAddressText(client);
    }

    /** La position de l'adresse de livraison de la commande ; à défaut, celle du siège du client. */
    private GeoLocation deliveryLocation(OrderInfo order, ClientInfo client) {
        if (order.deliveryAddressId() != null) {
            Optional<DeliveryAddressInfo> address = clientLookup.findAddress(client.id(), order.deliveryAddressId());
            if (address.isPresent()) {
                return address.get().location();
            }
        }
        return client.location();
    }

    private static String clientAddressText(ClientInfo client) {
        return Stream.of(client.addressLine1(), client.addressLine2(),
                        ((client.postalCode() != null ? client.postalCode() + " " : "")
                                + (client.city() != null ? client.city() : "")).trim(),
                        client.countryCode())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        OrderInfo order = orderLookup.findOrder(shipment.getOrderId()).orElseThrow();
        ClientInfo client = clientLookup.findById(order.clientId()).orElseThrow();
        Map<UUID, OrderInfo.Line> orderLines = order.lines().stream()
                .collect(Collectors.toMap(OrderInfo.Line::id, Function.identity()));
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                shipment.getLines().stream().map(ShipmentLine::getArticleId).collect(Collectors.toSet()));
        Map<UUID, String> lotNumbers = stockLookup.lotNumbers(
                shipment.getLines().stream().map(ShipmentLine::getLotId).filter(Objects::nonNull)
                        .collect(Collectors.toSet()));

        List<ShipmentResponse.Line> lines = shipment.getLines().stream()
                .map(line -> {
                    ArticleInfo article = articles.get(line.getArticleId());
                    OrderInfo.Line orderLine = orderLines.get(line.getOrderLineId());
                    return new ShipmentResponse.Line(line.getId(), line.getOrderLineId(), line.getArticleId(),
                            article != null ? article.code() : null, article != null ? article.label() : null,
                            line.getLotId(), line.getLotId() != null ? lotNumbers.get(line.getLotId()) : null,
                            line.getQuantity(), orderLine != null ? orderLine.quantity() : 0,
                            orderLine != null ? orderLine.remaining() : 0);
                })
                .sorted(Comparator.comparing(ShipmentResponse.Line::articleLabel, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        String addressText = shipment.getDeliveryAddressText() != null
                ? shipment.getDeliveryAddressText()
                : deliveryAddressText(order, client);
        return new ShipmentResponse(shipment.getId(), shipment.getCode(), shipment.getStatus(), order.id(), order.code(),
                order.customerReference(), order.incoterm(), client.id(), client.code(), client.name(),
                clientAddressText(client), client.vatNumber(), client.exciseNumber(), client.language(), addressText,
                deliveryLocation(order, client), shipment.getPlannedDate(), shipment.getCarrier(), shipment.getTransportReference(), shipment.getNotes(),
                lines, shipment.totalBottles(), shipment.getShippedAt(), shipment.getShippedBy(),
                shipment.getCreatedBy(), shipment.getCreatedAt(), shipment.getUpdatedAt());
    }

    /** EX-26-001 : c'est aussi le numéro du bon de livraison. */
    private String nextCode(LocalDate plannedDate) {
        String prefix = "EX-%02d-".formatted(plannedDate.getYear() % 100);
        int max = shipmentRepository.findCodesLike(prefix + "%").stream()
                .map(code -> code.substring(prefix.length()))
                .filter(suffix -> !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return prefix + "%03d".formatted(max + 1);
    }

    private Shipment findShipment(UUID id) {
        return shipmentRepository.findWithLinesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expédition", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

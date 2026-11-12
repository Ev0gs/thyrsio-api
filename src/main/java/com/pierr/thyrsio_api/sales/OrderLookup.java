package com.pierr.thyrsio_api.sales;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** La porte d'entrée des autres modules vers les commandes (lecture seule). */
@Service
public class OrderLookup {

    private final CustomerOrderRepository orderRepository;
    private final CustomerOrderLineRepository lineRepository;

    public OrderLookup(CustomerOrderRepository orderRepository, CustomerOrderLineRepository lineRepository) {
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
    }

    @Transactional(readOnly = true)
    public Optional<OrderInfo> findOrder(UUID orderId) {
        return orderRepository.findWithLinesById(orderId).map(OrderInfo::of);
    }

    @Transactional(readOnly = true)
    public Map<UUID, OrderInfo> findOrders(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findWithLinesByIdIn(orderIds).stream()
                .map(OrderInfo::of)
                .collect(Collectors.toMap(OrderInfo::id, Function.identity()));
    }

    /** Des lignes de commande, avec leur commande, indexées par id de ligne. */
    @Transactional(readOnly = true)
    public Map<UUID, OrderInfo.Line> findLines(Collection<UUID> lineIds) {
        if (lineIds.isEmpty()) {
            return Map.of();
        }
        return lineRepository.findWithOrderByIdIn(lineIds).stream()
                .map(OrderInfo::lineOf)
                .collect(Collectors.toMap(OrderInfo.Line::id, Function.identity()));
    }
}

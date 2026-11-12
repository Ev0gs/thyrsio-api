package com.pierr.thyrsio_api.shipping;

import com.pierr.thyrsio_api.sales.OrderShipments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Les expéditions d'une commande : l'implémentation de l'interface du module Ventes. */
@Component
class ShippingOrderShipments implements OrderShipments {

    private final ShipmentRepository shipmentRepository;

    ShippingOrderShipments(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Summary> forOrder(UUID orderId) {
        return shipmentRepository.findByOrderIdOrderByPlannedDateAscCreatedAtAsc(orderId).stream()
                .map(s -> new Summary(s.getId(), s.getCode(), s.getStatus().name(), s.getPlannedDate(),
                        s.getShippedAt(), s.totalBottles()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDraft(UUID orderId) {
        return shipmentRepository.existsByOrderIdAndStatus(orderId, ShipmentStatus.DRAFT);
    }
}

package com.pierr.thyrsio_api.sales;

import com.pierr.thyrsio_api.referentiel.Incoterm;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ce que les autres modules (Production, Expéditions) ont le droit de savoir d'une commande. */
public record OrderInfo(
        UUID id,
        String code,
        OrderStatus status,
        UUID clientId,
        UUID deliveryAddressId,
        String customerReference,
        LocalDate requestedLoadingDate,
        Incoterm incoterm,
        List<Line> lines
) {
    public record Line(UUID id, UUID orderId, String orderCode, OrderStatus orderStatus, UUID clientId,
                       UUID articleId, int quantity, int shippedQuantity, LocalDate requestedLoadingDate) {
        public int remaining() {
            return quantity - shippedQuantity;
        }
    }

    static Line lineOf(CustomerOrderLine line) {
        CustomerOrder order = line.getOrder();
        return new Line(line.getId(), order.getId(), order.getCode(), order.getStatus(), order.getClientId(),
                line.getArticleId(), line.getQuantity(), line.getShippedQuantity(), order.getRequestedLoadingDate());
    }

    static OrderInfo of(CustomerOrder order) {
        return new OrderInfo(order.getId(), order.getCode(), order.getStatus(), order.getClientId(),
                order.getDeliveryAddressId(), order.getCustomerReference(), order.getRequestedLoadingDate(),
                order.getIncoterm(), order.getLines().stream().map(OrderInfo::lineOf).toList());
    }
}

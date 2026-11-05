package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.entity.ShippingAddress;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** [Phase 4 · 6단계] 주문 상세. {@code paymentKey}와 {@code idempotencyKey}는 내보내지 않는다. */
public record OrderDetailResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        String recipientName,
        String recipientPhone,
        String address,
        List<OrderItemResponse> items,
        LocalDateTime createdAt
) {

    public static OrderDetailResponse from(Order order) {
        ShippingAddress shipping = order.getShippingAddress();
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                shipping.getRecipientName(),
                shipping.getRecipientPhone(),
                shipping.getAddress(),
                order.getOrderItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt());
    }
}

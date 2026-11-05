package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.OrderItem;

import java.math.BigDecimal;

/** [Phase 4 · 6단계] 주문 항목. 상품명과 단가는 주문 시점 스냅샷이다. */
public record OrderItemResponse(Long productId, String productName, int quantity, BigDecimal priceAtOrder) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(), item.getProductNameAtOrder(), item.getQuantity(), item.getPriceAtOrder());
    }
}

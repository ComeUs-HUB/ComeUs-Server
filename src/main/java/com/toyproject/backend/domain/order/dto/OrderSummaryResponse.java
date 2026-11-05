package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** [Phase 4 · 6단계] 주문 목록의 한 칸. 항목은 상세에서만 읽어 목록에 쿼리가 늘지 않게 한다. */
public record OrderSummaryResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }
}

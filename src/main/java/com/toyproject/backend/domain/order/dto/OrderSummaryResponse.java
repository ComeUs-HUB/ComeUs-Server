package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** [Phase 4 · 6단계] 회원 주문 목록의 한 칸. PK를 내보내지 않고 {@code orderNumber}로 가리킨다. */
public record OrderSummaryResponse(
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getOrderNumber(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }
}

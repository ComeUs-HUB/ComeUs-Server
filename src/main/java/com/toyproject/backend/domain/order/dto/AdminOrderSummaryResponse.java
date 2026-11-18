package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** [Phase 4 · 7단계] ADMIN 주문 목록의 한 칸. 상태 변경 API가 PK를 쓰므로 함께 내보낸다. */
public record AdminOrderSummaryResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {

    public static AdminOrderSummaryResponse from(Order order) {
        return new AdminOrderSummaryResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }
}

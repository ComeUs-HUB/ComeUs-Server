package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.Order;

import java.math.BigDecimal;

/** [Phase 4 · 5단계] 주문 생성 응답. 두 값으로 FE가 토스 결제창을 연다. */
public record OrderCreateResponse(String orderNumber, BigDecimal amount) {

    public static OrderCreateResponse from(Order order) {
        return new OrderCreateResponse(order.getOrderNumber(), order.getTotalAmount());
    }
}

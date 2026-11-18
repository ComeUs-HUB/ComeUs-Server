package com.toyproject.backend.domain.order.entity;

/**
 * [Phase 4 · 4단계] 주문 상태. 허용되는 전이는 {@code docs/domains/purchase/order.md}의 상태 전환표가 정한다.
 *
 * <p>DB에는 네이티브 ENUM이 아니라 {@code VARCHAR(20)}으로 저장된다.
 */
public enum OrderStatus {

    /** 결제 대기. 재고를 점유하고 있다. */
    PENDING,
    /** 결제 승인 진행 중. 취소할 수 없다. */
    PAYMENT_IN_PROGRESS,
    /** 결제 완료. */
    PAID,
    /** [Phase 4 · 12단계] 토스 결제 취소 진행 중. 취소·배송 처리할 수 없다(ADR 0029). */
    REFUND_IN_PROGRESS,
    /** 배송 중. */
    SHIPPING,
    /** 배송 완료. 최종 상태다. */
    DELIVERED,
    /** 취소. 점유한 재고를 복구한 최종 상태다. */
    CANCELLED
}

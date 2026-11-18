package com.toyproject.backend.domain.payment.entity;

/**
 * [Phase 4 · 9단계] 결제 상태. 승인에 성공한 결제만 저장하므로 실패 상태가 없다.
 */
public enum PaymentStatus {

    SUCCESS,
    /** 전액 취소됨. 부분 환불은 열지 않는다(ADR 0029). */
    REFUNDED
}

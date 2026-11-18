package com.toyproject.backend.domain.payment.exception;

import lombok.Getter;

/**
 * [Phase 4 · 9단계] 토스가 요청을 처리하지 않았다고 확인된 실패. 결제·취소는 일어나지 않았다.
 *
 * <p>호출자는 이때만 상태를 되돌린다. 어떤 코드가 여기 해당하는지는 ADR 0028·0029가 정한다.
 */
@Getter
public class PaymentRejectedException extends RuntimeException {

    private final String code;

    public PaymentRejectedException(String code, String tossMessage) {
        super(code + ": " + tossMessage);
        this.code = code;
    }
}

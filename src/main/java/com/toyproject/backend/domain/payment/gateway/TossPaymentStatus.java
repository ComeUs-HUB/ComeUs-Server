package com.toyproject.backend.domain.payment.gateway;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

/**
 * [Phase 4 · 9단계] 토스 결제 상태. 모르는 값은 {@link #UNKNOWN}으로 받는다.
 *
 * <p>토스가 상태를 추가해도 역직렬화가 실패하지 않고, 판정에서 "그 밖"으로 처리된다.
 */
public enum TossPaymentStatus {

    READY,
    IN_PROGRESS,
    WAITING_FOR_DEPOSIT,
    DONE,
    CANCELED,
    PARTIAL_CANCELED,
    ABORTED,
    EXPIRED,
    UNKNOWN;

    @JsonCreator
    public static TossPaymentStatus from(String value) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}

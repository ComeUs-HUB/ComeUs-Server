package com.toyproject.backend.domain.payment.dto;

import com.toyproject.backend.domain.payment.entity.Payment;
import com.toyproject.backend.domain.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [Phase 4 · 10단계] 결제 정보 응답. 승인 성공, 같은 키의 재요청, 결제 조회가 같은 형식이다.
 *
 * <p>결제 PK와 {@code paymentKey}는 내보내지 않고 결제는 {@code orderNumber}로 가리킨다.
 */
public record PaymentResponse(
        String orderNumber,
        BigDecimal amount,
        String method,
        PaymentStatus status,
        LocalDateTime paidAt
) {

    public static PaymentResponse of(String orderNumber, Payment payment) {
        return new PaymentResponse(orderNumber, payment.getAmount(), payment.getMethod(), payment.getStatus(),
                payment.getPaidAt());
    }
}

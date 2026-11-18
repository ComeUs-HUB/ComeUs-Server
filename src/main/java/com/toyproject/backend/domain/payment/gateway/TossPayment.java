package com.toyproject.backend.domain.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * [Phase 4 · 9단계] 토스 Payment 객체 중 우리가 쓰는 필드만 담는다.
 *
 * <p>필드 목록은 {@code payment.md}의 "응답" 표가 정한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(
        String paymentKey,
        String orderId,
        TossPaymentStatus status,
        BigDecimal totalAmount,
        String method,
        OffsetDateTime approvedAt,
        List<Cancel> cancels
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancel(OffsetDateTime canceledAt) {
    }

    /** 다른 주문의 {@code paymentKey}로 승인되는 것을 막는다(ADR 0028). */
    public boolean matches(String orderNumber, BigDecimal orderAmount) {
        return orderNumber.equals(orderId) && totalAmount != null && totalAmount.compareTo(orderAmount) == 0;
    }

    public LocalDateTime approvedAtLocal() {
        return toLocal(approvedAt);
    }

    /** 전액 취소만 하므로 취소 이력은 하나다. 없으면 null. */
    public LocalDateTime canceledAtLocal() {
        return (cancels == null || cancels.isEmpty()) ? null : toLocal(cancels.get(cancels.size() - 1).canceledAt());
    }

    // 오프셋을 떼지 않고 서버 시간대로 옮긴다. 다른 시각 컬럼이 서버 시간대 기준이다
    private static LocalDateTime toLocal(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}


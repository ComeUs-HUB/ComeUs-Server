package com.toyproject.backend.domain.payment.gateway;

import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import java.math.BigDecimal;

/**
 * [Phase 4 · 9단계] PG 호출 경계. 서비스는 토스 에러 코드를 모르고 두 예외로만 분기한다.
 *
 * <p>확정 거절과 결과 불명을 합치면 결과 불명 주문을 되돌려 중복 청구가 날 수 있다(ADR 0028).
 */
public interface PaymentGateway {

    /**
     * 결제를 승인한다. {@code orderId}는 {@code Order.orderNumber}다.
     *
     * @throws PaymentRejectedException      토스가 승인을 거절했다고 확인될 때
     * @throws PaymentResultUnknownException 그 밖의 모든 실패
     */
    TossPayment confirm(String paymentKey, String orderId, BigDecimal amount);

    /**
     * 결제를 조회한다. 복구 배치가 쓴다.
     *
     * @throws PaymentRejectedException      {@code NOT_FOUND_PAYMENT}일 때
     * @throws PaymentResultUnknownException 그 밖의 모든 실패
     */
    TossPayment getPayment(String paymentKey);

    /**
     * 결제를 전액 취소한다.
     *
     * @throws PaymentRejectedException      토스가 취소를 거절했다고 확인될 때
     * @throws PaymentResultUnknownException 그 밖의 모든 실패
     */
    TossPayment cancel(String paymentKey, String cancelReason);
}

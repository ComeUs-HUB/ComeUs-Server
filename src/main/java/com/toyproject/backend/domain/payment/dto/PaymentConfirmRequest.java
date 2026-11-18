package com.toyproject.backend.domain.payment.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * [Phase 4 · 10단계] 결제 승인 요청. FE가 토스 successUrl에서 받은 쿼리 값을 그대로 보낸다.
 *
 * <p>{@code orderId}는 토스 쪽 이름이고 값은 {@code Order.orderNumber}다.
 * {@code amount}는 리다이렉트 쿼리라 사용자가 고칠 수 있어 서비스가 주문 금액과 대조한다.
 */
public record PaymentConfirmRequest(
        @NotBlank(message = "paymentKey는 필수입니다.")
        @Size(max = 200, message = "paymentKey는 200자 이하입니다.")
        String paymentKey,

        @NotBlank(message = "orderId는 필수입니다.")
        @Size(max = 64, message = "orderId는 64자 이하입니다.")
        String orderId,

        @NotNull(message = "amount는 필수입니다.")
        @Positive(message = "amount는 양수입니다.")
        @Digits(integer = 12, fraction = 0, message = "amount는 원 단위 정수입니다.")
        BigDecimal amount
) {
}

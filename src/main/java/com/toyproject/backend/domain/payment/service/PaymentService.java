package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.payment.dto.PaymentConfirmRequest;
import com.toyproject.backend.domain.payment.dto.PaymentResponse;
import com.toyproject.backend.domain.payment.exception.PaymentErrorCode;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.PaymentGateway;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import com.toyproject.backend.domain.payment.service.PaymentTransitions.ClaimedOrder;
import com.toyproject.backend.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [Phase 4 · 10단계] 결제 승인. 선점 TX → 토스 호출 → 반영 TX 순서다(ADR 0026).
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. 토스 호출이 트랜잭션 안에 들어가면
 * 응답을 기다리는 동안 커넥션과 행 잠금을 잡고, 롤백해도 토스의 승인은 되돌아가지 않는다.
 *
 * <p>결과를 모르면 상태를 되돌리지 않고 {@code PAY003}으로 답한다. 복구 배치(11단계)가 토스에 물어 판정한다.
 * 계약은 {@code docs/domains/purchase/payment.md}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransitions transitions;
    private final PaymentGateway paymentGateway;

    /**
     * 토스 승인을 한 번만 부르고 결과를 주문에 반영한다.
     *
     * @throws BusinessException O001, PAY002~PAY006 — 경우는 {@code payment.md}의 결과별 응답 표
     */
    public PaymentResponse confirm(Long userId, PaymentConfirmRequest request) {
        ClaimedOrder claimed = transitions.claim(userId, request);
        String paymentKey = request.paymentKey();
        if (!claimed.claimed()) {
            return transitions.resolveUnclaimed(claimed.orderId(), paymentKey);
        }

        TossPayment toss;
        try {
            // 자동 재시도하지 않는다. 요청 수명이 늘어나고, 결과 판정은 복구 배치가 토스 조회로 한다(ADR 0030)
            toss = paymentGateway.confirm(paymentKey, claimed.orderNumber(), claimed.amount());
        } catch (PaymentRejectedException e) {
            throw rejected(claimed, paymentKey, e);
        } catch (PaymentResultUnknownException e) {
            if (e.isAlert()) {
                log.error("결제 승인 결과 불명(경보) orderNumber={} code={}", claimed.orderNumber(), e.getCode(), e);
            } else {
                log.warn("결제 승인 결과 불명 orderNumber={} code={}", claimed.orderNumber(), e.getCode(), e);
            }
            throw new BusinessException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }

        // 다른 주문의 paymentKey로 승인을 받아 오는 것을 막는다(ADR 0028 응답 검증)
        if (toss.status() != TossPaymentStatus.DONE || !toss.matches(claimed.orderNumber(), claimed.amount())) {
            log.error("결제 승인 응답이 주문과 다름 orderNumber={} tossOrderId={} tossAmount={} tossStatus={}",
                    claimed.orderNumber(), toss.orderId(), toss.totalAmount(), toss.status());
            throw new BusinessException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }
        return applySuccess(claimed, paymentKey, toss);
    }

    private PaymentResponse applySuccess(ClaimedOrder claimed, String paymentKey, TossPayment toss) {
        Optional<PaymentResponse> applied;
        try {
            applied = transitions.applySuccess(claimed.orderId(), claimed.orderNumber(), paymentKey, toss);
        } catch (RuntimeException e) {
            // 토스는 승인했다. 실패로 답하면 사용자가 다시 결제하므로 진행 중으로 답하고 배치에 맡긴다
            log.error("결제 승인 반영 실패 orderNumber={}", claimed.orderNumber(), e);
            throw new BusinessException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }
        if (applied.isPresent()) {
            return applied.get();
        }
        // 0건: 배치가 먼저 반영했으면 정상이다. 아니면 토스 결과와 주문이 어긋났다(ADR 0030)
        return transitions.findPaid(claimed.orderId(), paymentKey).orElseThrow(() -> {
            log.error("결제 승인 반영 0건, 토스는 승인 orderNumber={} currentStatus={}",
                    claimed.orderNumber(), transitions.currentStatus(claimed.orderId()));
            return new BusinessException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        });
    }

    private BusinessException rejected(ClaimedOrder claimed, String paymentKey, PaymentRejectedException e) {
        log.info("결제 승인 거절 orderNumber={} code={}", claimed.orderNumber(), e.getCode());
        try {
            if (!transitions.applyRejection(claimed.orderId(), paymentKey)) {
                OrderStatus current = transitions.currentStatus(claimed.orderId());
                if (current != OrderStatus.PENDING) {
                    log.error("결제 거절 반영 0건 orderNumber={} currentStatus={}", claimed.orderNumber(), current);
                }
            }
        } catch (RuntimeException ex) {
            // 주문은 PAYMENT_IN_PROGRESS로 남고 배치가 조회해 거절로 판정한다
            log.error("결제 거절 반영 실패 orderNumber={}", claimed.orderNumber(), ex);
        }
        return new BusinessException(PaymentErrorCode.PAYMENT_REJECTED);
    }
}

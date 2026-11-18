package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.service.OrderService;
import com.toyproject.backend.domain.payment.exception.PaymentErrorCode;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.PaymentGateway;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import com.toyproject.backend.domain.payment.service.RefundTransitions.ClaimedRefund;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [Phase 4 · 12단계] 환불. 선점 TX → 토스 취소 → 반영 TX 순서다(ADR 0029). 승인의 {@link PaymentService}와 같은 모양이다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. 토스 취소는 되돌릴 수 없어 트랜잭션 안에 넣지 않는다.
 * <p>결과를 모르면 상태를 되돌리지 않고 {@code PAY008}로 답한다. 복구 배치가 토스에 물어 판정한다.
 * 계약은 {@code docs/domains/purchase/payment.md}의 "환불".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundTransitions transitions;
    private final PaymentGateway paymentGateway;
    private final OrderService orderService;

    /**
     * {@code PAID} 주문을 전액 환불하고 취소한다. 진입점은 {@code OrderCancelService}다.
     *
     * @throws BusinessException 선점 실패면 지금 상태로 O002·O003·O004, 확정 거절 PAY007, 결과 불명 PAY008
     */
    public void refund(Long orderId, String cancelReason) {
        ClaimedRefund claimed = transitions.claim(orderId);
        if (!claimed.claimed()) {
            throw orderService.cancelRejection(orderId);
        }

        TossPayment toss;
        try {
            // 자동 재시도하지 않는다. 결과 판정은 복구 배치가 토스 조회로 한다(ADR 0030)
            toss = paymentGateway.cancel(claimed.paymentKey(), cancelReason);
        } catch (PaymentRejectedException e) {
            throw rejected(claimed, e);
        } catch (PaymentResultUnknownException e) {
            if (e.isAlert()) {
                log.error("환불 결과 불명(경보) orderNumber={} code={}", claimed.orderNumber(), e.getCode(), e);
            } else {
                log.warn("환불 결과 불명 orderNumber={} code={}", claimed.orderNumber(), e.getCode(), e);
            }
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }

        if (toss.status() != TossPaymentStatus.CANCELED || !claimed.paymentKey().equals(toss.paymentKey())
                || !toss.matches(claimed.orderNumber(), claimed.amount())) {
            log.error("환불 응답이 주문과 다름 orderNumber={} tossPaymentKey={} tossOrderId={} tossStatus={}",
                    claimed.orderNumber(), toss.paymentKey(), toss.orderId(), toss.status());
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }
        applySuccess(claimed, toss);
    }

    private void applySuccess(ClaimedRefund claimed, TossPayment toss) {
        boolean applied;
        try {
            applied = transitions.applySuccess(claimed.orderId(), toss);
        } catch (RuntimeException e) {
            // 토스는 환불했다. 실패로 답하면 사용자가 다시 취소하므로 진행 중으로 답하고 배치에 맡긴다
            log.error("환불 반영 실패 orderNumber={}", claimed.orderNumber(), e);
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }
        if (applied) {
            return;
        }
        // 0건: 배치가 먼저 반영했으면 정상이다. 아니면 토스 결과와 주문이 어긋났다
        OrderStatus current = orderService.getOrderStatus(claimed.orderId());
        if (current != OrderStatus.CANCELLED) {
            log.error("환불 반영 0건, 토스는 취소 orderNumber={} currentStatus={}", claimed.orderNumber(), current);
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }
    }

    private BusinessException rejected(ClaimedRefund claimed, PaymentRejectedException e) {
        // 사용자가 고칠 수 있는 원인이 아니라 확정 거절도 ERROR다. 고객센터가 결제사 쪽 사유를 확인한다(ADR 0029)
        log.error("환불 거절 orderNumber={} code={}", claimed.orderNumber(), e.getCode());
        try {
            if (!transitions.applyRejection(claimed.orderId())) {
                OrderStatus current = orderService.getOrderStatus(claimed.orderId());
                if (current != OrderStatus.PAID) {
                    log.error("환불 거절 반영 0건 orderNumber={} currentStatus={}", claimed.orderNumber(), current);
                }
            }
        } catch (RuntimeException ex) {
            // 주문은 REFUND_IN_PROGRESS로 남고 배치가 조회해 DONE이면 PAID로 돌린다
            log.error("환불 거절 반영 실패 orderNumber={}", claimed.orderNumber(), ex);
        }
        return new BusinessException(PaymentErrorCode.PAYMENT_REFUND_REJECTED);
    }
}

package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.order.service.OrderService;
import com.toyproject.backend.domain.payment.entity.Payment;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Phase 4 · 12단계] 환불의 트랜잭션 구간. 토스 취소는 여기 없고 {@link RefundService}가 그 사이에 한다(ADR 0029).
 *
 * <p>{@link PaymentTransitions}와 같은 이유로 빈을 나눈다. 복구 배치도 같은 반영 메서드를 쓴다(ADR 0030).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundTransitions {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    /** 선점 결과. {@code claimed}가 false면 그사이 상태가 바뀌었다. 나머지 값은 PAID 뒤로 바뀌지 않는다. */
    public record ClaimedRefund(Long orderId, String orderNumber, BigDecimal amount, String paymentKey,
                                boolean claimed) {
    }

    /** {@code PAID → REFUND_IN_PROGRESS}로 선점한다. 배송 처리도 {@code PAID}에 대한 CAS라 둘 중 하나만 된다. */
    @Transactional
    public ClaimedRefund claim(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        int updated = orderRepository.transitionStatus(orderId, OrderStatus.PAID, OrderStatus.REFUND_IN_PROGRESS,
                LocalDateTime.now());
        return new ClaimedRefund(order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getPaymentKey(),
                updated == 1);
    }

    /**
     * 환불 성공을 반영한다. CAS가 1건일 때만 같은 트랜잭션에서 결제를 환불로 바꾸고 재고를 푼다.
     *
     * @return 반영했으면 true, 0건이면 false
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = PaymentTransitions.APPLY_TIMEOUT_SECONDS)
    public boolean applySuccess(Long orderId, TossPayment toss) {
        return applySuccess(orderId, toss, null);
    }

    /** 복구 배치용. {@code readUpdatedAt}의 뜻은 {@link PaymentTransitions}의 배치용 반영과 같다. */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = PaymentTransitions.APPLY_TIMEOUT_SECONDS)
    public boolean applySuccess(Long orderId, TossPayment toss, LocalDateTime readUpdatedAt) {
        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.applyRefundResult(orderId, readUpdatedAt,
                OrderStatus.REFUND_IN_PROGRESS, OrderStatus.CANCELLED, now);
        if (updated == 0) {
            return false;
        }
        LocalDateTime canceledAt = toss.canceledAtLocal();
        if (canceledAt == null) {
            log.warn("환불 응답에 취소 시각 없음, 반영 시각으로 저장 orderId={}", orderId);
            canceledAt = now;
        }
        // 이미 환불된 결제면 예외로 전체가 롤백된다
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        payment.refund(canceledAt);
        // 토스 취소가 끝난 뒤에 재고를 푼다(ADR 0029)
        orderService.restoreStock(orderId);
        return true;
    }

    /**
     * 환불 확정 거절을 반영해 {@code PAID}로 돌린다.
     *
     * @return 반영했으면 true, 0건이면 false
     */
    @Transactional(timeout = PaymentTransitions.APPLY_TIMEOUT_SECONDS)
    public boolean applyRejection(Long orderId) {
        return applyRejection(orderId, null);
    }

    /** 복구 배치용. */
    @Transactional(timeout = PaymentTransitions.APPLY_TIMEOUT_SECONDS)
    public boolean applyRejection(Long orderId, LocalDateTime readUpdatedAt) {
        return orderRepository.applyRefundResult(orderId, readUpdatedAt,
                OrderStatus.REFUND_IN_PROGRESS, OrderStatus.PAID, LocalDateTime.now()) == 1;
    }
}

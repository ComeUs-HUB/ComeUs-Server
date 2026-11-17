package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.exception.OrderErrorCode;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.payment.dto.PaymentConfirmRequest;
import com.toyproject.backend.domain.payment.dto.PaymentResponse;
import com.toyproject.backend.domain.payment.entity.Payment;
import com.toyproject.backend.domain.payment.exception.PaymentErrorCode;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.repository.PaymentRepository;
import com.toyproject.backend.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Phase 4 · 10단계] 결제 승인의 트랜잭션 구간. 토스 호출은 여기 없고 {@link PaymentService}가 그 사이에 한다.
 *
 * <p>{@code PaymentService}가 자기 메서드를 부르면 프록시를 타지 않아 트랜잭션이 걸리지 않으므로 빈을 나눈다.
 * 11단계 복구 배치도 같은 반영 메서드를 쓴다(ADR 0030).
 *
 * <p><b>0건 뒤의 재확인은 새 트랜잭션에서 한다.</b> MySQL 기본 격리 수준(REPEATABLE READ)에서는 CAS가 0건으로 끝난 뒤
 * 같은 트랜잭션에서 다시 읽으면 처음 읽은 시점의 스냅샷이 보여 다른 쪽이 커밋한 상태를 놓친다.
 */
@Component
@RequiredArgsConstructor
public class PaymentTransitions {

    /** 반영 트랜잭션의 상한. 요청 최대 수명을 정하는 값이다(ADR 0030). */
    static final int APPLY_TIMEOUT_SECONDS = 10;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /** 선점 결과. {@code claimed}가 false면 이미 다른 시도가 있었거나 승인할 수 없는 상태다. */
    public record ClaimedOrder(Long orderId, String orderNumber, BigDecimal amount, boolean claimed) {
    }

    /**
     * 주문을 확인하고 {@code PENDING → PAYMENT_IN_PROGRESS}로 선점한다. 금액이 다르면 토스를 부르지 않는다.
     *
     * @throws BusinessException 없거나 타인의 주문이면 O001, 금액이 다르면 PAY004
     */
    @Transactional
    public ClaimedOrder claim(Long userId, PaymentConfirmRequest request) {
        Order order = orderRepository.findByOrderNumberAndUserId(request.orderId(), userId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (request.amount().compareTo(order.getTotalAmount()) != 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        int updated = orderRepository.claimPayment(order.getId(), request.paymentKey(),
                OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS, LocalDateTime.now());
        return new ClaimedOrder(order.getId(), order.getOrderNumber(), order.getTotalAmount(), updated == 1);
    }

    /**
     * 선점이 0건일 때 현재 상태로 응답을 정한다. 같은 키로 이미 결제됐으면 응답을 잃은 FE의 재시도다.
     *
     * @throws BusinessException 진행 중이면 PAY003, 다른 키로 결제됐으면 PAY002, 그 밖의 상태면 PAY005
     */
    @Transactional(readOnly = true)
    public PaymentResponse resolveUnclaimed(Long orderId, String paymentKey) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        return switch (order.getStatus()) {
            case PAYMENT_IN_PROGRESS -> throw new BusinessException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
            case PAID -> paymentRepository.findByOrderId(orderId)
                    .filter(payment -> payment.getPaymentKey().equals(paymentKey))
                    .map(payment -> PaymentResponse.of(order.getOrderNumber(), payment))
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_EXISTS));
            default -> throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_ALLOWED);
        };
    }

    /**
     * 승인 성공을 반영한다. CAS가 1건일 때만 같은 트랜잭션에서 결제를 저장한다.
     *
     * <p>선점 여부와 무관한 값만 받는다. 선점하지 않는 복구 배치도 이 메서드를 쓴다.
     *
     * @param orderNumber 응답용. 반영 조건에는 쓰지 않는다
     *
     * @return 반영했으면 결제 정보, 0건이면 empty
     */
    @Transactional(timeout = APPLY_TIMEOUT_SECONDS)
    public Optional<PaymentResponse> applySuccess(Long orderId, String orderNumber, String paymentKey, TossPayment toss) {
        int updated = orderRepository.applyPaymentSuccess(orderId, paymentKey,
                OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PAID, LocalDateTime.now());
        if (updated == 0) {
            return Optional.empty();
        }
        Payment payment = paymentRepository.save(Payment.success(orderRepository.getReferenceById(orderId), toss));
        return Optional.of(PaymentResponse.of(orderNumber, payment));
    }

    /**
     * 승인 거절을 반영해 {@code PENDING}으로 돌린다.
     *
     * @return 반영했으면 true, 0건이면 false
     */
    @Transactional(timeout = APPLY_TIMEOUT_SECONDS)
    public boolean applyRejection(Long orderId, String paymentKey) {
        return orderRepository.applyPaymentRejection(orderId, paymentKey,
                OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PENDING, LocalDateTime.now()) == 1;
    }

    /** 반영이 0건일 때, 다른 경로가 같은 키로 먼저 성공을 반영했는지 본다. */
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> findPaid(Long orderId, String paymentKey) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getStatus() == OrderStatus.PAID)
                .flatMap(order -> paymentRepository.findByOrderId(orderId)
                        .filter(payment -> payment.getPaymentKey().equals(paymentKey))
                        .map(payment -> PaymentResponse.of(order.getOrderNumber(), payment)));
    }

    /** 반영이 0건일 때 현재 상태를 본다. */
    @Transactional(readOnly = true)
    public OrderStatus currentStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }
}

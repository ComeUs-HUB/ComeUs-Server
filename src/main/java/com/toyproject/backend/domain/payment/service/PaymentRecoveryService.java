package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.PaymentGateway;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.service.PaymentTransitions.RecoveryTarget;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * [Phase 4 · 11단계] 결과를 모른 채 멈춘 {@code PAYMENT_IN_PROGRESS} 주문을 토스 조회로 판정한다.
 *
 * <p>트랜잭션이 없다. 읽기와 반영은 {@link PaymentTransitions}가 각자 열고, 토스 조회는 그 사이에 한다(ADR 0026).
 * 판정 표는 {@code payment.md}의 "복구 배치", 실행 규칙은 ADR 0030.
 *
 * <p><b>판정하지 못하면 어떤 컬럼도 건드리지 않는다.</b> {@code updated_at}이 바뀌면 1시간 종결 시계가 리셋된다.
 * <p>복구 판정은 1분마다 같은 주문을 다시 보므로 불일치·경보 상태도 WARN이다. 1시간 경보가 ERROR로 한 번 알린다(ADR 0030).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    /** 요청 최대 수명(약 100초)보다 길어야 살아 있는 요청과 겹치지 않는다. */
    static final Duration JUDGE_AFTER = Duration.ofMinutes(5);
    /** 이보다 오래 판정되지 않으면 수동 처리로 넘긴다(ADR 0028). */
    static final Duration ABANDON_AFTER = Duration.ofHours(1);
    static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final PaymentTransitions transitions;
    private final PaymentGateway paymentGateway;

    /** 판정 대상 id. {@code updated_at}이 5분 ~ 1시간 전인 주문을 오래된 순으로 100건. */
    public List<Long> findTargetIds(LocalDateTime now) {
        return orderRepository.findRecoveryTargetIds(OrderStatus.PAYMENT_IN_PROGRESS,
                now.minus(ABANDON_AFTER), now.minus(JUDGE_AFTER), PageRequest.of(0, BATCH_SIZE));
    }

    /** 1시간 경계를 넘긴 주문번호. {@code from}이 null이면 넘긴 주문 전체다. */
    public List<String> findAbandonedOrderNumbers(LocalDateTime from, LocalDateTime now) {
        return orderRepository.findAbandonedOrderNumbers(OrderStatus.PAYMENT_IN_PROGRESS, from, now.minus(ABANDON_AFTER));
    }

    /** 주문 하나를 판정한다. 조회 대상이 된 뒤 다른 경로가 먼저 처리했으면 아무것도 하지 않는다. */
    public void recover(Long orderId, LocalDateTime now) {
        RecoveryTarget target = transitions.readForRecovery(orderId);
        if (!isJudgeable(target, now)) {
            return;
        }

        TossPayment toss;
        try {
            toss = paymentGateway.getPayment(target.paymentKey());
        } catch (PaymentRejectedException e) {
            // 조회 거절은 NOT_FOUND_PAYMENT뿐이다 — 토스에 결제가 없다
            reject(target, "조회 " + e.getCode());
            return;
        } catch (PaymentResultUnknownException e) {
            log.warn("복구 조회 실패, 다음 주기에 다시 조회 orderNumber={} code={} alert={}",
                    target.orderNumber(), e.getCode(), e.isAlert(), e);
            return;
        }

        switch (toss.status()) {
            case DONE -> {
                if (toss.matches(target.orderNumber(), target.amount())) {
                    approve(target, toss);
                } else {
                    log.warn("복구 조회 DONE이지만 주문과 다름 orderNumber={} tossOrderId={} tossAmount={}",
                            target.orderNumber(), toss.orderId(), toss.totalAmount());
                }
            }
            case ABORTED, EXPIRED -> reject(target, toss.status().name());
            case READY, IN_PROGRESS -> log.debug("복구 조회 아직 진행 중 orderNumber={} status={}",
                    target.orderNumber(), toss.status());
            default -> log.warn("복구 조회 판정 불가 상태 orderNumber={} status={}",
                    target.orderNumber(), toss.status());
        }
    }

    // 조회 대상이 된 뒤 읽기 전에 요청이나 다른 인스턴스가 처리했을 수 있다
    private static boolean isJudgeable(RecoveryTarget target, LocalDateTime now) {
        return target.status() == OrderStatus.PAYMENT_IN_PROGRESS
                && target.paymentKey() != null
                && target.updatedAt().isAfter(now.minus(ABANDON_AFTER))
                && !target.updatedAt().isAfter(now.minus(JUDGE_AFTER));
    }

    private void approve(RecoveryTarget target, TossPayment toss) {
        boolean applied = transitions.applySuccess(target.orderId(), target.orderNumber(), target.paymentKey(), toss,
                target.updatedAt()).isPresent();
        if (applied) {
            log.info("복구 승인 반영 orderNumber={}", target.orderNumber());
        } else {
            log.warn("복구 승인 반영 0건, 다른 경로가 먼저 처리 orderNumber={}", target.orderNumber());
        }
    }

    private void reject(RecoveryTarget target, String reason) {
        boolean applied = transitions.applyRejection(target.orderId(), target.paymentKey(), target.updatedAt());
        if (applied) {
            log.info("복구 거절 반영 orderNumber={} reason={}", target.orderNumber(), reason);
        } else {
            log.warn("복구 거절 반영 0건, 다른 경로가 먼저 처리 orderNumber={} reason={}", target.orderNumber(), reason);
        }
    }
}

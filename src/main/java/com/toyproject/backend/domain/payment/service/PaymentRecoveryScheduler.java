package com.toyproject.backend.domain.payment.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [Phase 4 · 11단계] 복구 배치. 1분마다 멈춘 주문을 판정하고, 1시간을 넘긴 주문을 한 번 경보한다(ADR 0028·0030).
 *
 * <p>인스턴스마다 돈다. 여러 인스턴스가 같은 주문을 판정해도 반영 CAS가 읽은 {@code updated_at}을 비교해 한쪽만 반영된다.
 * <p>{@code fixedDelay}라 한 인스턴스 안에서는 겹치지 않는다. 만료 배치와는 스케줄러 스레드 2개로 서로를 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private static final long INTERVAL_MS = 60_000;
    /** 다음 주문을 시작하기 전에만 확인한다. 실제 상한은 이 값 + 한 건이다. */
    static final Duration RUN_BUDGET = Duration.ofSeconds(50);

    private final PaymentRecoveryService recoveryService;

    /** 직전 경보 조회의 경계 시각. 인스턴스 메모리라 재시작하면 null — 첫 실행은 넘긴 주문 전체를 경보한다. */
    private volatile LocalDateTime lastAlertCutoff;

    @Scheduled(initialDelay = INTERVAL_MS, fixedDelay = INTERVAL_MS)
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        recoverStuckOrders(now, RUN_BUDGET);
        alertAbandoned(now);
    }

    /** 판정 대상을 오래된 순으로 처리한다. 예산을 넘기면 남은 건은 다음 주기로 넘긴다. */
    void recoverStuckOrders(LocalDateTime now, Duration budget) {
        long startedAt = System.nanoTime();
        List<Long> targets = recoveryService.findTargetIds(now);
        for (int i = 0; i < targets.size(); i++) {
            if (Duration.ofNanos(System.nanoTime() - startedAt).compareTo(budget) > 0) {
                log.info("복구 배치 실행 예산 초과, 남은 {}건은 다음 주기", targets.size() - i);
                return;
            }
            Long orderId = targets.get(i);
            try {
                recoveryService.recover(orderId, now);
            } catch (RuntimeException e) {
                // 한 건의 실패가 나머지를 막지 않게 한다. 행을 건드리지 않았으니 다음 주기에 다시 판정한다
                log.warn("복구 판정 실패 orderId={}", orderId, e);
            }
        }
    }

    /** 직전 실행 이후 1시간 경계를 넘긴 주문만 경보한다. 조회가 성공한 뒤에만 경계를 옮긴다. */
    void alertAbandoned(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(PaymentRecoveryService.ABANDON_AFTER);
        List<String> orderNumbers;
        try {
            orderNumbers = recoveryService.findAbandonedOrderNumbers(lastAlertCutoff, now);
        } catch (RuntimeException e) {
            log.warn("1시간 경보 조회 실패, 다음 실행이 같은 구간부터 다시 본다", e);
            return;
        }
        if (!orderNumbers.isEmpty()) {
            // 건수 ERROR 한 줄 + 목록 WARN 한 줄. 수동 처리 절차는 payment.md "복구 배치"
            log.error("결제 1시간 미판정, 수동 처리 필요 count={}", orderNumbers.size());
            log.warn("결제 1시간 미판정 주문 orderNumbers={}", orderNumbers);
        }
        lastAlertCutoff = cutoff;
    }
}

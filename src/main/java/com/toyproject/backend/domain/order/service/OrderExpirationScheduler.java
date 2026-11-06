package com.toyproject.backend.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * [Phase 4 · 8단계] 결제되지 않은 {@code PENDING} 주문을 취소해 재고를 돌려준다.
 *
 * <p>주문마다 트랜잭션을 나누려고 {@link OrderService}와 빈을 분리한다. 자기 호출이면 격리 수준이 적용되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    /** 토스 승인 유효 시간 10분에 여유를 더한 값이다. */
    private static final Duration PENDING_EXPIRATION = Duration.ofMinutes(15);
    private static final long INTERVAL_MS = 60_000;

    private final OrderService orderService;

    @Scheduled(initialDelay = INTERVAL_MS, fixedDelay = INTERVAL_MS)
    public void expirePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(PENDING_EXPIRATION);
        for (Long orderId : orderService.findExpiredPendingOrderIds(cutoff)) {
            try {
                orderService.expirePendingOrder(orderId, cutoff);
            } catch (RuntimeException e) {
                // 한 건의 실패가 나머지를 막지 않게 한다. 다음 주기에 다시 시도한다
                log.warn("PENDING 주문 만료 실패 orderId={}", orderId, e);
            }
        }
    }
}

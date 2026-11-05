package com.toyproject.backend.domain.order.repository;

import com.toyproject.backend.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** [Phase 4 · 5단계] 주문 Repository. */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 멱등 재요청 판정. {@code carts} 잠금 뒤에 불러야 먼저 커밋된 주문이 보인다(ADR 0024). */
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}

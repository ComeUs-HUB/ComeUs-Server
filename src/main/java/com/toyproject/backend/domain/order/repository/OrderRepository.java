package com.toyproject.backend.domain.order.repository;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** [Phase 4 · 5단계] 주문 Repository. */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 멱등 재요청 판정. {@code carts} 잠금 뒤에 불러야 먼저 커밋된 주문이 보인다(ADR 0024). */
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** [Phase 4 · 6단계] */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** [Phase 4 · 6단계] 타인의 주문은 없는 주문과 같게 취급한다. */
    boolean existsByIdAndUserId(Long id, Long userId);

    /** [Phase 4 · 6단계] 상세 응답이 항목을 읽으므로 함께 조회한다. 상품은 스냅샷이 있어 읽지 않는다. */
    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findWithItemsByIdAndUserId(Long id, Long userId);

    /**
     * [Phase 4 · 6단계] 상태를 CAS로 전이한다. 벌크 UPDATE라 {@code updatedAt}을 직접 쓴다.
     *
     * @return 갱신된 행 수. 0이면 상태가 이미 달라졌다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :nextStatus, o.updatedAt = :updatedAt
             where o.id = :orderId
               and o.status = :expectedStatus
            """)
    int transitionStatus(@Param("orderId") Long orderId,
                         @Param("expectedStatus") OrderStatus expectedStatus,
                         @Param("nextStatus") OrderStatus nextStatus,
                         @Param("updatedAt") LocalDateTime updatedAt);
}

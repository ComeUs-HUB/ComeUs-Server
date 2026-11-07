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
import java.util.List;
import java.util.Optional;

/** [Phase 4 · 5단계] 주문 Repository. */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 멱등 재요청 판정. {@code carts} 잠금 뒤에 불러야 먼저 커밋된 주문이 보인다(ADR 0024). */
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** [Phase 4 · 6단계] */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** [Phase 4 · 6단계] 회원 API의 주문 식별. 타인의 주문은 없는 주문과 같게 취급한다. */
    @Query("select o.id from Order o where o.orderNumber = :orderNumber and o.user.id = :userId")
    Optional<Long> findIdByOrderNumberAndUserId(@Param("orderNumber") String orderNumber, @Param("userId") Long userId);

    /** [Phase 4 · 6단계] 상세 응답이 항목을 읽으므로 함께 조회한다. 상품은 스냅샷이 있어 읽지 않는다. */
    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findWithItemsByOrderNumberAndUserId(String orderNumber, Long userId);

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

    /** [Phase 4 · 8단계] {@code cutoff} 전부터 결제되지 않은 주문 id. 오래된 순이다. */
    @Query("""
            select o.id from Order o
             where o.status = :pending
               and o.updatedAt < :cutoff
             order by o.updatedAt
            """)
    List<Long> findExpiredPendingOrderIds(@Param("pending") OrderStatus pending,
                                          @Param("cutoff") LocalDateTime cutoff,
                                          Pageable pageable);

    /**
     * [Phase 4 · 8단계] 만료 주문을 취소한다. 조회 뒤 승인 거절로 돌아온 주문은 시각이 새로워 취소되지 않는다.
     *
     * @return 갱신된 행 수. 0이면 그사이 상태나 시각이 달라졌다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :cancelled, o.updatedAt = :updatedAt
             where o.id = :orderId
               and o.status = :pending
               and o.updatedAt < :cutoff
            """)
    int cancelExpiredPendingOrder(@Param("orderId") Long orderId,
                                  @Param("pending") OrderStatus pending,
                                  @Param("cancelled") OrderStatus cancelled,
                                  @Param("cutoff") LocalDateTime cutoff,
                                  @Param("updatedAt") LocalDateTime updatedAt);
}

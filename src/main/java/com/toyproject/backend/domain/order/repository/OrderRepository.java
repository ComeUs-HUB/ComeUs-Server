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
import java.util.Collection;
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

    /** [Phase 4 · 10단계] 결제 승인의 주문 식별. 타인의 주문은 없는 주문과 같게 취급한다. */
    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

    /**
     * [Phase 4 · 10단계] 결제 승인을 선점한다. 승인 진입은 이 CAS로 한 번만 허용된다(ADR 0026).
     * [PENDING -> PAYMENT_IN_PROGRESS]
     *
     * @return 갱신된 행 수. 0이면 이미 다른 시도가 선점했거나 승인할 수 없는 상태다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :inProgress, o.paymentKey = :paymentKey, o.updatedAt = :updatedAt
             where o.id = :orderId
               and o.status = :pending
            """)
    int claimPayment(@Param("orderId") Long orderId,
                     @Param("paymentKey") String paymentKey,
                     @Param("pending") OrderStatus pending,
                     @Param("inProgress") OrderStatus inProgress,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * [Phase 4 · 10단계] 승인 성공을 반영한다. {@code paymentKey}까지 비교해 이전 시도의 늦은 반영을 막는다.
     * [PAYMENT_IN_PROGRESS -> PAID]
     *
     * <p>{@code readUpdatedAt}이 null이면 요청 경로다. 복구 배치는 주문을 읽을 때 본 값을 넘겨,
     * 읽은 뒤 누가 행을 바꿨으면 0건이 되게 한다(ADR 0030).
     *
     * @return 갱신된 행 수. 0이면 다른 경로가 먼저 반영했거나 선점한 시도가 바뀌었다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :paid, o.updatedAt = :updatedAt
             where o.id = :orderId
               and o.status = :inProgress
               and o.paymentKey = :paymentKey
               and (:readUpdatedAt is null or o.updatedAt = :readUpdatedAt)
            """)
    int applyPaymentSuccess(@Param("orderId") Long orderId,
                            @Param("paymentKey") String paymentKey,
                            @Param("readUpdatedAt") LocalDateTime readUpdatedAt,
                            @Param("inProgress") OrderStatus inProgress,
                            @Param("paid") OrderStatus paid,
                            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * [Phase 4 · 10단계] 승인 거절을 반영한다. {@code paymentKey}를 비워 같은 주문으로 다시 결제할 수 있게 한다.
     * [PAYMENT_IN_PROGRESS -> PENDING]
     *
     * <p>{@code readUpdatedAt}의 뜻은 {@link #applyPaymentSuccess}와 같다.
     *
     * @return 갱신된 행 수. 0이면 다른 경로가 먼저 반영했거나 선점한 시도가 바뀌었다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :pending, o.paymentKey = null, o.updatedAt = :updatedAt
             where o.id = :orderId
               and o.status = :inProgress
               and o.paymentKey = :paymentKey
               and (:readUpdatedAt is null or o.updatedAt = :readUpdatedAt)
            """)
    int applyPaymentRejection(@Param("orderId") Long orderId,
                              @Param("paymentKey") String paymentKey,
                              @Param("readUpdatedAt") LocalDateTime readUpdatedAt,
                              @Param("inProgress") OrderStatus inProgress,
                              @Param("pending") OrderStatus pending,
                              @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * [Phase 4 · 12단계] 환불 결과를 반영한다. 성공은 {@code CANCELLED}, 확정 거절은 {@code PAID}로 보낸다.
     * [REFUND_IN_PROGRESS -> CANCELLED | PAID]
     *
     * <p>{@code payment_key}는 비교하지 않는다. 결제 하나에 전액 취소 한 번이라 키가 바뀌지 않는다(ADR 0029).
     * {@code readUpdatedAt}의 뜻은 {@link #applyPaymentSuccess}와 같다 — 배치의 옛 조회 결과가 새 환불 시도에 반영되는 것을 막는다(ADR 0030).
     *
     * @return 갱신된 행 수. 0이면 다른 경로가 먼저 반영했다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :nextStatus, o.updatedAt = :updatedAt
             where o.id = :orderId
               and o.status = :refundInProgress
               and (:readUpdatedAt is null or o.updatedAt = :readUpdatedAt)
            """)
    int applyRefundResult(@Param("orderId") Long orderId,
                          @Param("readUpdatedAt") LocalDateTime readUpdatedAt,
                          @Param("refundInProgress") OrderStatus refundInProgress,
                          @Param("nextStatus") OrderStatus nextStatus,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * [Phase 4 · 11단계] 복구 판정 대상 id. 12단계부터 승인·환불 진행 중 두 상태를 함께 본다. {@code updated_at}이 {@code (abandonCutoff, judgeCutoff]} 구간인 주문을 오래된 순으로.
     * {@code idx_orders_status_updated_at}을 탄다.
     */
    @Query("""
            select o.id from Order o
             where o.status in :statuses
               and o.updatedAt > :abandonCutoff
               and o.updatedAt <= :judgeCutoff
             order by o.updatedAt
            """)
    List<Long> findRecoveryTargetIds(@Param("statuses") Collection<OrderStatus> statuses,
                                     @Param("abandonCutoff") LocalDateTime abandonCutoff,
                                     @Param("judgeCutoff") LocalDateTime judgeCutoff,
                                     Pageable pageable);

    /**
     * [Phase 4 · 11단계] 1시간 경계를 넘긴 주문. {@code from}이 null이면(기동 후 첫 경보) 넘긴 주문 전체다.
     */
    @Query("""
            select o.orderNumber from Order o
             where o.status in :statuses
               and o.updatedAt <= :abandonCutoff
               and (:from is null or o.updatedAt > :from)
             order by o.updatedAt
            """)
    List<String> findAbandonedOrderNumbers(@Param("statuses") Collection<OrderStatus> statuses,
                                           @Param("from") LocalDateTime from,
                                           @Param("abandonCutoff") LocalDateTime abandonCutoff);
}

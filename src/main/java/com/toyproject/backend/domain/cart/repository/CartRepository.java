package com.toyproject.backend.domain.cart.repository;

import com.toyproject.backend.domain.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * [Phase 4 · 2단계] 장바구니 Repository. 항목은 {@link Cart}를 거쳐서만 쓰므로 {@code CartItemRepository}는 없다(ADR 0019).
 *
 * <p>쓰기 경로는 {@link #existsByUserId} → {@link #lockByUserId} → {@link #findByUserId} 순으로 부른다.
 * 없는 행을 잠그면 gap lock끼리 교착하고, 그래프 조회에 락을 함께 걸면 조인된 {@code products} 행까지 잠긴다.
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /** EntityGraph : 항목과 상품을 쿼리 1회로 읽는다. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByUserId(Long userId);

    /** 잠금 없는 존재 확인. 없는 행에 {@code FOR UPDATE}를 걸지 않기 위해 먼저 부른다. */
    boolean existsByUserId(Long userId);

    /** {@code carts} 행 하나만 잠근다. 존재가 확인된 뒤에만 부른다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.user.id = :userId")
    Optional<Cart> lockByUserId(@Param("userId") Long userId);
}

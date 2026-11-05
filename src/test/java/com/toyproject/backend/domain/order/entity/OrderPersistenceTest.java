package com.toyproject.backend.domain.order.entity;

import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.global.config.JpaConfig;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 4 · 4단계] {@link Order}의 uk 제약 검증.
 *
 * <p>H2 스키마는 엔티티에서 만들어지므로 {@code V5__create_orders.sql}은 여기서 검증되지 않는다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Order 영속성")
class OrderPersistenceTest {

    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    private TestEntityManager em;

    private User buyer;

    @BeforeEach
    void setUp() {
        buyer = em.persist(User.ofKakao("kakao-1", "buyer@example.com", "구매자"));
    }

    private static Order order(User user, String key) {
        return Order.create(user, key, ShippingAddress.of("구매자", "010-0000-0000", "서울시"));
    }

    @Test
    @DisplayName("uk_orders_user_id_idempotency_key — 같은 회원의 같은 키는 저장되지 않는다")
    void sameUserSameKeyViolatesUniqueKey() {
        em.persistAndFlush(order(buyer, KEY));

        assertThatThrownBy(() -> em.persistAndFlush(order(buyer, KEY)))
                .as("조회를 우회한 동시 요청의 최후 방어선이다 — ADR 0024")
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("uk_orders_user_id_idempotency_key"));
    }

    @Test
    @DisplayName("다른 회원은 같은 키를 쓸 수 있다")
    void otherUserCanUseSameKey() {
        User other = em.persist(User.ofKakao("kakao-2", "other@example.com", "다른회원"));
        em.persistAndFlush(order(buyer, KEY));

        Order saved = em.persistAndFlush(order(other, KEY));

        assertThat(saved.getId()).as("전역 유니크면 타인의 키를 선점해 주문을 막을 수 있다").isNotNull();
    }

    @Test
    @DisplayName("uk_orders_order_number — 주문번호는 중복될 수 없다")
    void duplicateOrderNumberViolatesUniqueKey() {
        Order first = em.persistAndFlush(order(buyer, KEY));
        Order second = order(buyer, "other-key");
        ReflectionTestUtils.setField(second, "orderNumber", first.getOrderNumber());

        assertThatThrownBy(() -> em.persistAndFlush(second))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("uk_orders_order_number"));
    }
}

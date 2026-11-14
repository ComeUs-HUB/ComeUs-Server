package com.toyproject.backend.domain.payment.entity;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.ShippingAddress;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.global.config.JpaConfig;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 4 · 9단계] {@link Payment}의 uk 제약 검증.
 *
 * <p>H2 스키마는 엔티티에서 만들어지므로 {@code V6__create_payments.sql}은 여기서 검증되지 않는다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Payment 영속성")
class PaymentPersistenceTest {

    @Autowired
    private TestEntityManager em;

    private Order order(String idempotencyKey) {
        User buyer = em.persist(User.ofKakao("kakao-" + idempotencyKey, idempotencyKey + "@example.com", "구매자"));
        return em.persist(Order.create(buyer, idempotencyKey, ShippingAddress.of("구매자", "010-0000-0000", "서울시")));
    }

    private static TossPayment approved(String paymentKey) {
        return new TossPayment(paymentKey, "ORD", TossPaymentStatus.DONE, new BigDecimal("19900"), "카드",
                OffsetDateTime.parse("2026-11-11T10:15:30+09:00"), null);
    }

    @Test
    @DisplayName("uk_payments_order_id — 한 주문에 결제는 하나뿐이다")
    void secondPaymentForSameOrderViolatesUniqueKey() {
        Order order = order("key-1");
        em.persistAndFlush(Payment.success(order, approved("pk-1")));

        assertThatThrownBy(() -> em.persistAndFlush(Payment.success(order, approved("pk-2"))))
                .as("승인 반영이 두 번 일어나도 결제가 둘이 되지 않는 최후 방어선이다")
                .isInstanceOf(ConstraintViolationException.class)
                // H2에선 @OneToOne이 만드는 이름 없는 unique가 먼저 걸린다. 이름 대신 컬럼으로 확인한다
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("order_id"));
    }

    @Test
    @DisplayName("uk_payments_payment_key — 한 결제 키는 한 주문에만 쓰인다")
    void samePaymentKeyForOtherOrderViolatesUniqueKey() {
        em.persistAndFlush(Payment.success(order("key-1"), approved("pk-1")));

        assertThatThrownBy(() -> em.persistAndFlush(Payment.success(order("key-2"), approved("pk-1"))))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("uk_payments_payment_key"));
    }
}

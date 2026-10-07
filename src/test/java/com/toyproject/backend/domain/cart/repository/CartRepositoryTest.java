package com.toyproject.backend.domain.cart.repository;

import com.toyproject.backend.domain.cart.entity.Cart;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.global.config.JpaConfig;
import org.hibernate.SessionFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 4 · 2단계] {@link CartRepository}의 조회 쿼리 수와 uk 제약 검증.
 *
 * <p>H2는 gap lock을 재현하지 않으므로 락의 교착 방지는 여기서 검증되지 않는다 — 3단계 MySQL 실기동에서 본다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CartRepository")
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TestEntityManager em;

    private User user;
    private Long cartId;
    private Long teeId;

    @BeforeEach
    void setUp() {
        Category tops = em.persist(Category.root("상의", 1));
        Category tshirt = em.persist(Category.child(tops, "티셔츠", 1));
        Product tee = em.persist(Product.create(tshirt, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), 10));
        Product shirt = em.persist(Product.create(tshirt, "Oxford 셔츠", "면 100%", new BigDecimal("49900.00"), 5));
        user = em.persist(User.ofKakao("kakao-1", "buyer@example.com", "구매자"));

        Cart cart = Cart.create(user);
        cart.addItem(tee, 2);
        cart.addItem(shirt, 1);
        em.persist(cart);
        em.flush();

        cartId = cart.getId();
        teeId = tee.getId();
        em.clear();
    }

    private Statistics statistics() {
        Statistics statistics = em.getEntityManager()
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    /** 응답이 읽는 값을 모두 건드린다. LAZY가 남아 있으면 여기서 쿼리가 나간다. */
    private static void touchResponseFields(Cart cart) {
        cart.getItems().forEach(item -> {
            item.getProduct().getName();
            item.isPurchasable();
        });
    }

    @Test
    @DisplayName("findByUserId — 항목과 상품을 쿼리 1회로 읽는다")
    void findByUserIdLoadsItemsAndProductsInOneQuery() {
        Statistics statistics = statistics();

        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        touchResponseFields(cart);

        assertThat(cart.getItems()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount())
                .as("@EntityGraph가 없으면 항목 1 + 상품 2가 더 나가 4가 된다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("잠금 후 그래프 조회 — 잠근 장바구니에도 항목과 상품이 추가 쿼리 없이 채워진다")
    void graphQueryInitializesItemsOfLockedCart() {
        Statistics statistics = statistics();

        Cart locked = cartRepository.lockByUserId(user.getId()).orElseThrow();
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        touchResponseFields(cart);

        assertThat(cart).as("같은 영속성 컨텍스트라 같은 인스턴스다").isSameAs(locked);
        assertThat(cart.getItems()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount())
                .as("잠금 1 + 그래프 1. 그래프가 이미 관리 중인 장바구니의 컬렉션을 채우지 못하면 LAZY 쿼리가 붙는다")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("uk_carts_user_id — 같은 회원의 두 번째 장바구니는 저장되지 않는다")
    void secondCartOfSameUserViolatesUniqueKey() {
        User sameUser = em.find(User.class, user.getId());

        assertThatThrownBy(() -> cartRepository.saveAndFlush(Cart.create(sameUser)))
                .as("동시 첫 담기의 최후 방어선이다. 전역 핸들러가 C005(409)로 응답한다")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uk_cart_items_cart_id_product_id — Cart를 우회해 같은 상품을 넣으면 거절된다")
    void duplicateProductRowViolatesUniqueKey() {
        assertThatThrownBy(() -> em.getEntityManager()
                .createNativeQuery("insert into cart_items (cart_id, product_id, quantity, created_at) values (?, ?, ?, ?)")
                .setParameter(1, cartId)
                .setParameter(2, teeId)
                .setParameter(3, 1)
                .setParameter(4, LocalDateTime.now())
                .executeUpdate())
                .as("합산은 Cart.addItem이 하고, 이 제약은 그것을 우회한 경로를 막는다. "
                        + "CartItem @Table에 선언이 없으면 H2 스키마에 제약이 없어 통과해 버린다")
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("uk_cart_items_cart_id_product_id"));
    }
}

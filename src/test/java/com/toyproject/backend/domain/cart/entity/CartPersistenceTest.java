package com.toyproject.backend.domain.cart.entity;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.global.config.JpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Phase 4 · 1단계] {@link Cart} 컬렉션에서 뺀 항목이 {@code orphanRemoval}로 DB에서도 지워지는지 검증.
 *
 * <p>flush·clear 후 DB를 다시 읽어 판정한다. 애노테이션 근거는 {@code CategoryRepositoryTest}와 같다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Cart 영속성")
class CartPersistenceTest {

    @Autowired
    private TestEntityManager em;

    private Long cartId;
    private Long teeItemId;
    private Long shirtItemId;

    @BeforeEach
    void setUp() {
        Category tops = em.persist(Category.root("상의", 1));
        Category tshirt = em.persist(Category.child(tops, "티셔츠", 1));
        Product tee = em.persist(Product.create(tshirt, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), 10));
        Product shirt = em.persist(Product.create(tshirt, "Oxford 셔츠", "면 100%", new BigDecimal("49900.00"), 5));
        User user = em.persist(User.ofKakao("kakao-1", "buyer@example.com", "구매자"));

        Cart cart = Cart.create(user);
        cart.addItem(tee, 2);
        cart.addItem(shirt, 1);
        em.persist(cart);
        em.flush();

        cartId = cart.getId();
        teeItemId = cart.getItems().get(0).getId();
        shirtItemId = cart.getItems().get(1).getId();
        em.clear();
    }

    @Test
    @DisplayName("removeItem — 뺀 항목의 행만 지워진다")
    void removeItemDeletesOrphanRow() {
        em.find(Cart.class, cartId).removeItem(teeItemId);
        em.flush();
        em.clear();

        assertThat(em.find(CartItem.class, teeItemId)).isNull();
        assertThat(em.find(CartItem.class, shirtItemId)).isNotNull();
        assertThat(em.find(Cart.class, cartId).getItems())
                .extracting(CartItem::getId)
                .containsExactly(shirtItemId);
    }

    @Test
    @DisplayName("clear — 항목 행은 모두 지워지고 장바구니 행은 남는다")
    void clearDeletesAllItemRowsButKeepsCart() {
        em.find(Cart.class, cartId).clear();
        em.flush();
        em.clear();

        Long remaining = em.getEntityManager()
                .createQuery("select count(ci) from CartItem ci where ci.cart.id = :cartId", Long.class)
                .setParameter("cartId", cartId)
                .getSingleResult();
        assertThat(remaining).isZero();
        assertThat(em.find(Cart.class, cartId)).isNotNull();
    }
}

package com.toyproject.backend.domain.cart.entity;

import com.toyproject.backend.domain.cart.exception.CartErrorCode;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 4 · 1단계] {@link Cart}의 합산·재고·종류 상한 불변식 검증
 *
 * <p>틀렸을 때 <b>조용히 잘못된 결과가 나가는</b> 경로만 담는다 — 합산 후 검사를 우회하는 담기,
 * id 없는 인스턴스의 오합산, 상한이 찬 뒤의 합산이 그것이다.
 *
 * <p>{@code orphanRemoval}의 실제 {@code DELETE}와 uk 제약은 {@code CartRepositoryTest},
 * {@code HIDDEN} 상품 차단과 타인 항목 404는 {@code CartServiceTest}가 소유한다.
 */
@DisplayName("Cart")
class CartTest {

    private static final BigDecimal PRICE = new BigDecimal("19900.00");
    private static final long ITEM_ID = 1L;

    private static User user() {
        return User.ofKakao("kakao-1", "buyer@example.com", "구매자");
    }

    private static Product product(int stock) {
        Category category = Category.child(Category.root("상의", 1), "티셔츠", 1);
        return Product.create(category, "기본 반팔티", "면 100%", PRICE, stock);
    }

    /** 영속 전 인스턴스는 id가 없다. {@code itemId}로 찾는 메서드를 검증하려면 주입해야 한다. */
    private static Cart cartWithItem(Product product, int quantity) {
        Cart cart = Cart.create(user());
        cart.addItem(product, quantity);
        ReflectionTestUtils.setField(cart.getItems().get(0), "id", ITEM_ID);
        return cart;
    }

    @Test
    @DisplayName("담기는 합산 후 수량으로 재고를 검사한다")
    void addChecksStockAgainstMergedQuantity() {
        Cart cart = Cart.create(user());
        Product product = product(2);

        cart.addItem(product, 1);
        cart.addItem(product, 1);

        assertThatThrownBy(() -> cart.addItem(product, 1))
                .as("검사 대상이 요청 수량이면 이 호출이 통과해 재고를 넘긴다")
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CartErrorCode.CART_QUANTITY_EXCEEDED);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity())
                .as("합산은 되고, 거절된 담기는 수량을 건드리지 않는다")
                .isEqualTo(2);
        assertThat(product.getStockQuantity())
                .as("담기는 재고를 점유하지 않는다 — ADR 0016")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("다른 상품은 합산되지 않는다")
    void differentProductsAreSeparateItems() {
        Cart cart = Cart.create(user());

        cart.addItem(product(10), 3);
        cart.addItem(product(10), 3);

        assertThat(cart.getItems())
                .as("id가 없는 서로 다른 인스턴스가 한 항목으로 합쳐지면 잘못된 주문이 나간다")
                .hasSize(2);
    }

    @Test
    @DisplayName("종류 상한은 새 종류에만 걸린다")
    void itemTypeLimitAppliesOnlyToNewTypes() {
        Cart cart = Cart.create(user());
        for (int i = 0; i < Cart.MAX_ITEM_TYPES; i++) {
            cart.addItem(product(10), 1);
        }

        assertThatThrownBy(() -> cart.addItem(product(10), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CartErrorCode.CART_ITEM_LIMIT_EXCEEDED);

        Product alreadyInCart = cart.getItems().get(0).getProduct();
        cart.addItem(alreadyInCart, 1);

        assertThat(cart.getItems())
                .as("합산은 종류를 늘리지 않으므로 상한이 찼어도 허용된다")
                .hasSize(Cart.MAX_ITEM_TYPES);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 항목은 CT001이다")
    void unknownItemRejected() {
        Cart cart = cartWithItem(product(10), 1);
        long unknownId = ITEM_ID + 1;

        assertThatThrownBy(() -> cart.changeQuantity(unknownId, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);

        assertThatThrownBy(() -> cart.removeItem(unknownId))
                .as("타인 항목을 404로 숨기는 계약이 두 경로 모두에 걸린다")
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("수량 변경은 교체이고, 재고를 넘기면 CT002로 거절된다")
    void changeQuantityReplacesAndChecksStock() {
        Cart cart = cartWithItem(product(10), 2);

        cart.changeQuantity(ITEM_ID, 5);

        assertThat(cart.getItems().get(0).getQuantity())
                .as("합산으로 잘못 구현하면 7이 되어 수량이 폭증한다")
                .isEqualTo(5);

        assertThatThrownBy(() -> cart.changeQuantity(ITEM_ID, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CartErrorCode.CART_QUANTITY_EXCEEDED);

        assertThat(cart.getItems().get(0).getQuantity())
                .as("거절된 변경은 수량을 건드리지 않는다")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("0·음수 수량은 IllegalArgumentException이다")
    void nonPositiveQuantityRejected() {
        Cart cart = cartWithItem(product(10), 1);
        Product other = product(10);

        assertThatThrownBy(() -> cart.addItem(other, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.addItem(other, -5))
                .as("음수를 허용하면 합산이 음수가 되어 CT002 검사가 우회된다")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.changeQuantity(ITEM_ID, 0))
                .as("0은 삭제로 해석하지 않는다 — DELETE가 따로 있다")
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("getItems는 불변 목록이다")
    void itemsAreUnmodifiable() {
        Cart cart = cartWithItem(product(10), 1);
        CartItem item = cart.getItems().get(0);

        assertThatThrownBy(() -> cart.getItems().add(item))
                .as("밖에서 넣을 수 있으면 재고 검사와 종류 상한을 모두 우회한다")
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package com.toyproject.backend.domain.order.entity;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Phase 4 · 4단계] {@link Order}의 가격 스냅샷·총액 불변식 검증.
 */
@DisplayName("Order")
class OrderTest {

    private static final Category CATEGORY = Category.child(Category.root("상의", 1), "티셔츠", 1);

    private static Product product(String price) {
        return Product.create(CATEGORY, "기본 반팔티", "면 100%", new BigDecimal(price), 10);
    }

    private static Order order() {
        return Order.create(
                User.ofKakao("kakao-1", "buyer@example.com", "구매자"),
                "550e8400-e29b-41d4-a716-446655440000",
                ShippingAddress.of("구매자", "010-0000-0000", "서울시"));
    }

    @Test
    @DisplayName("총액은 항목별 스냅샷 가격 × 수량의 합이다")
    void totalAmountSumsLineAmounts() {
        Order order = order();

        order.addItem(product("19900.00"), 2);
        order.addItem(product("49900.50"), 1);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("89700.50");
    }

    @Test
    @DisplayName("상품 가격이 바뀌어도 항목 가격과 총액은 그대로다")
    void priceChangeDoesNotAffectPlacedOrder() {
        Order order = order();
        Product product = product("19900.00");
        order.addItem(product, 1);

        product.updateInfo(CATEGORY, "기본 반팔티", "면 100%", new BigDecimal("29900.00"), 10);

        assertThat(order.getOrderItems().get(0).getPriceAtOrder()).isEqualByComparingTo("19900.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("19900.00");
    }
}

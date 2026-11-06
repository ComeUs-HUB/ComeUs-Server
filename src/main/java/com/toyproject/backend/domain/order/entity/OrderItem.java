package com.toyproject.backend.domain.order.entity;

import com.toyproject.backend.domain.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [Phase 4 · 4단계] 주문 항목. 주문 시점 상품명과 가격을 스냅샷해 이후 상품이 바뀌어도 기록이 유지된다.
 *
 * <p>생성 경로가 {@link Order}뿐이고 이후 바뀌지 않는다.
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "product_name_at_order", nullable = false, length = 100)
    private String productNameAtOrder;

    @Column(name = "price_at_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrder;

    private OrderItem(Order order, Product product, int quantity) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.productNameAtOrder = product.getName();
        this.priceAtOrder = product.getPrice();
    }

    /** {@link Order}만 호출한다. */
    static OrderItem of(Order order, Product product, int quantity) {
        return new OrderItem(order, product, quantity);
    }

    BigDecimal lineAmount() {
        return priceAtOrder.multiply(BigDecimal.valueOf(quantity));
    }
}

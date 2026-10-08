package com.toyproject.backend.domain.cart.entity;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * [Phase 4 · 1단계] 장바구니 항목. 상품과 수량만 담고 가격은 담지 않는다 —
 * 주문 시점 가격 스냅샷은 {@code OrderItem.priceAtOrder}가 가진다.
 *
 * <p>생성·수정 경로가 {@link Cart}뿐이다 — 정적 팩터리와 {@link #changeQuantity(int)}가
 * package-private이라 재고 검사를 우회해 수량을 바꿀 수 없다.
 */
@Entity
// 장바구니당 상품 1행. V4에만 두면 H2(@DataJpaTest)에서 재현되지 않아 @Table에도 선언한다(User와 같다)
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_items_cart_id_product_id", columnNames = {"cart_id", "product_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private CartItem(Cart cart, Product product, int quantity) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
    }

    /** {@link Cart}만 호출한다. */
    static CartItem of(Cart cart, Product product, int quantity) {
        return new CartItem(cart, product, quantity);
    }

    /** [Phase 4 · 2단계] 지금 이 수량으로 주문이 통과하는지. 판매 중이고 재고 이하여야 한다. */
    public boolean isPurchasable() {
        return product.getStatus() == ProductStatus.ON_SALE && quantity <= product.getStockQuantity();
    }

    /** 재고 검사를 끝낸 {@link Cart}만 호출한다. */
    void changeQuantity(int quantity) {
        this.quantity = quantity;
    }
}

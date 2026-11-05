package com.toyproject.backend.domain.cart.entity;

import com.toyproject.backend.domain.cart.exception.CartErrorCode;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.global.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * [Phase 4 · 1단계] 장바구니 엔티티. 회원 1명당 하나이며 첫 담기에서 만든다.
 *
 * <p>동일 상품은 한 항목으로 합산하고 재고 검사는 합산 후 수량으로 한다 —
 * 1개씩 나눠 담아 재고를 넘기는 경로가 없다. 이 검사는 재고를 점유하지 않는다(ADR 0016).
 *
 * <p>계약은 {@code docs/domains/purchase/cart.md}.
 */
@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Cart {

    /** 담을 수 있는 상품 종류 상한. 합산은 종류를 늘리지 않으므로 새 항목이 생길 때만 걸린다. */
    public static final int MAX_ITEM_TYPES = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Cart(User user) {
        this.user = user;
    }

    /** 가입이 아니라 첫 담기에서 만든다 — 빈 장바구니 행을 남기지 않는다. */
    public static Cart create(User user) {
        return new Cart(user);
    }

    /**
     * 상품 담기. 동일 상품이면 수량을 합산한다.
     *
     * @throws BusinessException        CT003·CT002
     * @throws IllegalArgumentException {@code quantity}가 1 미만이면
     */
    public void addItem(Product product, int quantity) {
        requirePositive(quantity);

        CartItem found = findByProduct(product);
        if (found == null) {
            requireRoomForNewType();
        }

        // int로 더하면 기존 수량 + Integer.MAX_VALUE가 음수로 넘쳐 재고 검사를 통과한다
        long merged = (found == null ? 0L : found.getQuantity()) + quantity;
        requireWithinStock(product, merged);

        if (found == null) {
            this.items.add(CartItem.of(this, product, quantity));
        } else {
            found.changeQuantity((int) merged);
        }
    }

    /**
     * 수량 변경. 합산이 아니라 교체다.
     *
     * @throws BusinessException        항목이 없으면 CT001(404), 재고를 넘으면 CT002(409)
     * @throws IllegalArgumentException {@code quantity}가 1 미만이면
     */
    public void changeQuantity(Long itemId, int quantity) {
        requirePositive(quantity);

        CartItem item = getItem(itemId);
        requireWithinStock(item.getProduct(), quantity);
        item.changeQuantity(quantity);
    }

    /** @throws BusinessException 항목이 없으면 CT001(404) */
    public void removeItem(Long itemId) {
        this.items.remove(getItem(itemId));
    }

    /**
     * [Phase 4 · 5단계] 주문할 항목을 고른다.
     *
     * @throws BusinessException 없는 항목이 하나라도 있으면 CT001(404)
     */
    public List<CartItem> selectItems(Collection<Long> itemIds) {
        return new LinkedHashSet<>(itemIds).stream().map(this::getItem).toList();
    }

    /** [Phase 4 · 5단계] 주문이 성립한 항목만 지운다. 선택하지 않은 항목은 남는다. */
    public void removeItems(Collection<Long> itemIds) {
        this.items.removeIf(item -> itemIds.contains(item.getId()));
    }

    /** 비어 있어도 실패하지 않는다. */
    public void clear() {
        this.items.clear();
    }

    /** 밖에서 컬렉션을 고치면 합산 불변식이 깨지므로 불변 목록을 돌려준다. */
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    private CartItem getItem(Long itemId) {
        return this.items.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    private CartItem findByProduct(Product product) {
        for (CartItem item : this.items) {
            Product held = item.getProduct();
            // 영속 전 인스턴스는 id가 없으므로 동일성을 함께 본다
            if (held == product
                    || (held.getId() != null && held.getId().equals(product.getId()))) {
                return item;
            }
        }
        return null;
    }

    private void requireRoomForNewType() {
        if (this.items.size() >= MAX_ITEM_TYPES) {
            throw new BusinessException(CartErrorCode.CART_ITEM_LIMIT_EXCEEDED);
        }
    }

    /** 담기 시점 검사일 뿐 재고를 점유하지 않는다 — 최종 판정은 주문 생성의 P002다(ADR 0016). */
    private static void requireWithinStock(Product product, long quantity) {
        if (quantity > product.getStockQuantity()) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_EXCEEDED);
        }
    }

    /** 0 이하는 호출자 버그다 — 요청 DTO가 C001로 먼저 거른다. */
    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("장바구니 수량은 1 이상이어야 합니다: " + quantity);
        }
    }
}

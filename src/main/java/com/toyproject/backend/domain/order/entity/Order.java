package com.toyproject.backend.domain.order.entity;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * [Phase 4 · 4단계] 주문. 장바구니를 값으로 복사해 성립하고 연결을 남기지 않는다(ADR 0017).
 *
 * <p>중복 생성은 멱등키 uk가 막고(ADR 0024) 승인 진입은 상태 CAS가 막는다(ADR 0026).
 * <p>계약은 {@code docs/domains/purchase/order.md}.
 */
@Entity
// V5에만 두면 H2(@DataJpaTest)에서 재현되지 않아 @Table에도 선언한다(User와 같다)
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"),
                @UniqueConstraint(name = "uk_orders_user_id_idempotency_key",
                        columnNames = {"user_id", "idempotency_key"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 토스 {@code orderId}로 쓴다. PK를 쓰면 주문 순번이 노출된다. */
    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /** 승인을 선점한 시도의 키. 거절로 {@code PENDING}에 돌아오면 비운다(ADR 0026). */
    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** 결제 금액 검증의 기준값이라 파생값이어도 저장한다. */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Embedded
    private ShippingAddress shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 만료·복구 배치의 기준값. 상태 CAS는 벌크 UPDATE라 이 값을 문장에서 직접 갱신해야 한다. */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Order(User user, String orderNumber, String idempotencyKey, ShippingAddress shippingAddress) {
        this.user = user;
        this.orderNumber = orderNumber;
        this.idempotencyKey = idempotencyKey;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.PENDING;
        this.totalAmount = BigDecimal.ZERO;
    }

    public static Order create(User user, String idempotencyKey, ShippingAddress shippingAddress) {
        return new Order(user, generateOrderNumber(), idempotencyKey, shippingAddress);
    }

    /**
     * 현재 상품 가격을 스냅샷해 항목을 더하고 총액을 올린다. 재고 차감은 호출자가 한다.
     *
     * @throws IllegalArgumentException {@code quantity}가 1 미만이면
     */
    public void addItem(Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다: " + quantity);
        }
        OrderItem item = OrderItem.of(this, product, quantity);
        this.orderItems.add(item);
        this.totalAmount = this.totalAmount.add(item.lineAmount());
    }

    /** 밖에서 컬렉션을 고치면 총액과 어긋나므로 불변 목록을 돌려준다. */
    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(this.orderItems);
    }

    /** 토스 {@code orderId} 규칙(6~64자, 영문·숫자·{@code -_=})을 따른다. */
    private static String generateOrderNumber() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return "ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + random;
    }
}

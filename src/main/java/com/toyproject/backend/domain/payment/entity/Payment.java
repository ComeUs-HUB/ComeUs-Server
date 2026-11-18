package com.toyproject.backend.domain.payment.entity;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * [Phase 4 · 9단계] 토스 승인에 성공한 결제. 거절은 행으로 남기지 않는다.
 *
 * <p>거절을 저장하면 그 주문의 재시도가 {@code uk_payments_order_id}에 막힌다.
 * <p>계약은 {@code docs/domains/purchase/payment.md}.
 */
@Entity
// V6에만 두면 H2(@DataJpaTest)에서 재현되지 않아 @Table에도 선언한다(Order와 같다)
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"),
                @UniqueConstraint(name = "uk_payments_payment_key", columnNames = "payment_key")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "payment_key", nullable = false, length = 200)
    private String paymentKey;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** 토스 문자열 그대로. 새 결제 수단이 와도 반영이 실패하지 않게 enum으로 두지 않는다. */
    @Column(nullable = false, length = 30)
    private String method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Payment(Order order, String paymentKey, BigDecimal amount, String method, LocalDateTime paidAt) {
        this.order = order;
        this.paymentKey = paymentKey;
        // 컬럼 자릿수에 맞춰 저장 직후의 응답과 다시 읽은 응답이 같은 값이 되게 한다
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        this.method = method;
        this.paidAt = paidAt;
        this.status = PaymentStatus.SUCCESS;
    }

    /** 승인 응답으로 결제를 만든다. {@code orderId}·금액 검증은 호출자가 먼저 한다. */
    public static Payment success(Order order, TossPayment toss) {
        return new Payment(order, toss.paymentKey(), toss.totalAmount(), toss.method(), toss.approvedAtLocal());
    }

    /**
     * 전액 환불로 바꾼다.
     *
     * @throws IllegalStateException 이미 환불된 결제면
     */
    public void refund(LocalDateTime canceledAt) {
        if (status != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("환불할 수 없는 결제 상태입니다: " + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = canceledAt;
    }
}

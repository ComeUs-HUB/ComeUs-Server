package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.entity.ShippingAddress;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGateway;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGatewayConfig;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import com.toyproject.backend.domain.payment.entity.PaymentStatus;
import com.toyproject.backend.domain.payment.repository.PaymentRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Phase 4 · 11단계] 복구 배치의 판정, 읽은 뒤의 경합, 실행 예산, 1시간 경보를 실제 트랜잭션으로 검증한다.
 * 12단계부터 환불 판정도 검증한다.
 *
 * <p>{@code updated_at}은 {@link JdbcTemplate}으로 과거로 돌린다. 시각은 인자로 넘긴다 — 8단계 만료 배치와 같은 방식.
 */
@SpringBootTest
@Import(FakePaymentGatewayConfig.class)
@DisplayName("결제 복구 배치 (Phase 4 · 11단계)")
class PaymentRecoveryTest {

    private static final ShippingAddress ADDRESS = ShippingAddress.of("구매자", "010-0000-0000", "서울시");
    private static final BigDecimal PRICE = new BigDecimal("19900");

    @Autowired private PaymentRecoveryService recoveryService;
    @Autowired private PaymentTransitions paymentTransitions;
    @Autowired private FakePaymentGateway gateway;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    // DATETIME(6)과 맞춘다. 나노초가 남으면 DB에 쓴 값과 비교가 어긋난다
    private final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private Long buyer;
    private Long productId;

    @BeforeEach
    void setUp() {
        gateway.reset();
        buyer = userRepository.save(User.ofKakao("kakao-recovery", "buyer@example.com", "구매자")).getId();
        Category root = categoryRepository.save(Category.root("상의", 1));
        Category child = categoryRepository.save(Category.child(root, "티셔츠", 1));
        productId = productRepository.save(
                Product.create(child, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), 10)).getId();
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll(categoryRepository.findAll().stream()
                .filter(c -> c.getParent() != null).toList());
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** {@code paymentKey}로 선점된 채 {@code minutesAgo}분 전부터 멈춘 주문. */
    private Order stuckOrder(String paymentKey, long minutesAgo) {
        Order order = transactionTemplate.execute(status -> {
            Order created = Order.create(userRepository.getReferenceById(buyer), UUID.randomUUID().toString(), ADDRESS);
            created.addItem(productRepository.getReferenceById(productId), 1);
            return orderRepository.save(created);
        });
        transactionTemplate.executeWithoutResult(status -> orderRepository.claimPayment(order.getId(), paymentKey,
                OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS, LocalDateTime.now()));
        touch(order.getId(), now.minusMinutes(minutesAgo));
        return order;
    }

    /** 승인 반영까지 거친 뒤 환불을 선점한 채 {@code minutesAgo}분 전부터 멈춘 주문. 결제 1건이 있다. */
    private Order refundingOrder(String paymentKey, long minutesAgo) {
        Order order = stuckOrder(paymentKey, minutesAgo);
        assertThat(paymentTransitions.applySuccess(order.getId(), order.getOrderNumber(), paymentKey,
                lookup(paymentKey, order.getOrderNumber(), TossPaymentStatus.DONE, PRICE))).isPresent();
        transactionTemplate.executeWithoutResult(status -> orderRepository.transitionStatus(order.getId(),
                OrderStatus.PAID, OrderStatus.REFUND_IN_PROGRESS, LocalDateTime.now()));
        touch(order.getId(), now.minusMinutes(minutesAgo));
        return order;
    }

    private int stock() {
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }

    private void touch(Long orderId, LocalDateTime updatedAt) {
        jdbcTemplate.update("update orders set updated_at = ? where id = ?", updatedAt, orderId);
    }

    private Order current(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    private static TossPayment lookup(String paymentKey, String orderNumber, TossPaymentStatus status, BigDecimal amount) {
        return new TossPayment(paymentKey, orderNumber, status, amount, "카드",
                OffsetDateTime.parse("2026-11-17T10:15:30+09:00"), null);
    }

    @Nested
    @DisplayName("주문 하나의 판정")
    class Recover {

        private Order order;
        private LocalDateTime stuckAt;

        @BeforeEach
        void stuck() {
            order = stuckOrder("pk-1", 10);
            stuckAt = current(order).getUpdatedAt();
        }

        private void assertUntouched() {
            Order after = current(order);
            assertThat(after.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
            assertThat(after.getPaymentKey()).isEqualTo("pk-1");
            assertThat(after.getUpdatedAt()).as("판정하지 못하면 1시간 시계를 리셋하지 않는다").isEqualTo(stuckAt);
            assertThat(paymentRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("DONE + 일치 — PAID와 결제 1건")
        void doneAndMatchedIsPaid() {
            gateway.onLookup(() -> lookup("pk-1", order.getOrderNumber(), TossPaymentStatus.DONE, PRICE));

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(paymentRepository.findAll()).hasSize(1);
            assertThat(gateway.calls()).containsExactly("getPayment:pk-1");
        }

        @Test
        @DisplayName("DONE + 금액 불일치 — 아무것도 바꾸지 않는다")
        void doneButMismatchedIsUntouched() {
            gateway.onLookup(() -> lookup("pk-1", order.getOrderNumber(), TossPaymentStatus.DONE, new BigDecimal("100")));

            recoveryService.recover(order.getId(), now);

            assertUntouched();
        }

        @ParameterizedTest
        @EnumSource(value = TossPaymentStatus.class, names = {"ABORTED", "EXPIRED"})
        @DisplayName("ABORTED·EXPIRED — PENDING으로 되돌리고 키를 비운다")
        void abortedOrExpiredIsRejected(TossPaymentStatus status) {
            gateway.onLookup(() -> lookup("pk-1", order.getOrderNumber(), status, PRICE));

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(current(order).getPaymentKey()).isNull();
        }

        @Test
        @DisplayName("조회 거절(NOT_FOUND_PAYMENT) — PENDING으로 되돌린다")
        void lookupRejectedIsRejected() {
            gateway.onLookup(() -> { throw new PaymentRejectedException("NOT_FOUND_PAYMENT", "결제 없음"); });

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(current(order).getPaymentKey()).isNull();
        }

        @ParameterizedTest
        @EnumSource(value = TossPaymentStatus.class,
                names = {"READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT", "CANCELED", "PARTIAL_CANCELED", "UNKNOWN"})
        @DisplayName("판정할 수 없는 상태 — 아무것도 바꾸지 않는다")
        void undecidableStatusIsUntouched(TossPaymentStatus status) {
            gateway.onLookup(() -> lookup("pk-1", order.getOrderNumber(), status, PRICE));

            recoveryService.recover(order.getId(), now);

            assertUntouched();
        }

        @Test
        @DisplayName("조회 결과 불명 — 아무것도 바꾸지 않는다")
        void lookupUnknownIsUntouched() {
            gateway.onLookup(() -> { throw new PaymentResultUnknownException(null, false, "타임아웃", null); });

            recoveryService.recover(order.getId(), now);

            assertUntouched();
        }

        @Test
        @DisplayName("읽은 뒤 행이 바뀜 — 상태·키가 같아도 updated_at이 달라 0건")
        void changedAfterReadIsNotApplied() {
            LocalDateTime touchedAt = now.minusMinutes(6);
            gateway.onLookup(() -> {
                touch(order.getId(), touchedAt);   // 조회 중 다른 경로가 행을 바꿨다
                return lookup("pk-1", order.getOrderNumber(), TossPaymentStatus.DONE, PRICE);
            });

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
            assertThat(current(order).getUpdatedAt()).isEqualTo(touchedAt);
            assertThat(paymentRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("읽은 뒤 거절 → 재선점으로 키가 바뀜 — 옛 조회 결과가 새 시도에 반영되지 않는다")
        void reclaimedAfterReadIsPreserved() {
            gateway.onLookup(() -> {
                jdbcTemplate.update("update orders set payment_key = ?, updated_at = ? where id = ?",
                        "pk-2", now, order.getId());
                return lookup("pk-1", order.getOrderNumber(), TossPaymentStatus.ABORTED, PRICE);
            });

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
            assertThat(current(order).getPaymentKey()).isEqualTo("pk-2");
        }

        @Test
        @DisplayName("대상 구간 밖(5분 안) — 토스를 부르지 않는다")
        void tooRecentIsSkipped() {
            touch(order.getId(), now.minusMinutes(2));

            recoveryService.recover(order.getId(), now);

            assertThat(gateway.calls()).isEmpty();
        }
    }

    @Nested
    @DisplayName("환불 주문 하나의 판정 (12단계)")
    class RecoverRefund {

        private Order order;
        private LocalDateTime stuckAt;

        @BeforeEach
        void stuck() {
            order = refundingOrder("pk-r", 10);
            stuckAt = current(order).getUpdatedAt();
            gateway.reset();
        }

        private TossPayment refundLookup(TossPaymentStatus status) {
            return new TossPayment("pk-r", order.getOrderNumber(), status, PRICE, "카드",
                    OffsetDateTime.parse("2026-11-17T10:15:30+09:00"),
                    List.of(new TossPayment.Cancel(OffsetDateTime.parse("2026-11-17T10:20:00+09:00"))));
        }

        private void assertUntouched() {
            Order after = current(order);
            assertThat(after.getStatus()).isEqualTo(OrderStatus.REFUND_IN_PROGRESS);
            assertThat(after.getUpdatedAt()).as("판정하지 못하면 1시간 시계를 리셋하지 않는다").isEqualTo(stuckAt);
            assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS);
            assertThat(stock()).isEqualTo(10);
        }

        @Test
        @DisplayName("CANCELED — CANCELLED, 결제 REFUNDED, 재고 복구")
        void canceledIsRefunded() {
            gateway.onLookup(() -> refundLookup(TossPaymentStatus.CANCELED));

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.REFUNDED);
            assertThat(stock()).isEqualTo(11);
            assertThat(gateway.calls()).containsExactly("getPayment:pk-r");
        }

        @Test
        @DisplayName("DONE — 취소되지 않았다, PAID로 복귀")
        void doneReturnsToPaid() {
            gateway.onLookup(() -> refundLookup(TossPaymentStatus.DONE));

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(stock()).isEqualTo(10);
        }

        @Test
        @DisplayName("조회 거절(NOT_FOUND_PAYMENT) — 승인 판정과 달리 PENDING으로 가지 않고 그대로")
        void lookupRejectedIsUntouched() {
            gateway.onLookup(() -> { throw new PaymentRejectedException("NOT_FOUND_PAYMENT", "결제 없음"); });

            recoveryService.recover(order.getId(), now);

            assertUntouched();
            assertThat(current(order).getPaymentKey()).isEqualTo("pk-r");
        }

        @ParameterizedTest
        @EnumSource(value = TossPaymentStatus.class, names = {"CANCELED", "DONE"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("판정할 수 없는 상태 — 아무것도 바꾸지 않는다")
        void undecidableStatusIsUntouched(TossPaymentStatus status) {
            gateway.onLookup(() -> refundLookup(status));

            recoveryService.recover(order.getId(), now);

            assertUntouched();
        }

        @Test
        @DisplayName("주문과 다른 조회 결과 — 아무것도 바꾸지 않는다")
        void mismatchedIsUntouched() {
            gateway.onLookup(() -> new TossPayment("pk-r", order.getOrderNumber(), TossPaymentStatus.CANCELED,
                    new BigDecimal("100"), "카드", null, null));

            recoveryService.recover(order.getId(), now);

            assertUntouched();
        }

        @Test
        @DisplayName("조회 결과 불명 — 아무것도 바꾸지 않는다")
        void lookupUnknownIsUntouched() {
            gateway.onLookup(() -> { throw new PaymentResultUnknownException(null, false, "타임아웃", null); });

            recoveryService.recover(order.getId(), now);

            assertUntouched();
        }

        @Test
        @DisplayName("읽은 뒤 PAID 복귀 → 재취소로 새 시도 — 옛 조회 결과(DONE)가 새 시도를 PAID로 되돌리지 않는다")
        void newRefundAttemptAfterReadIsPreserved() {
            LocalDateTime retriedAt = now.minusMinutes(6);
            gateway.onLookup(() -> {
                // 다른 인스턴스가 PAID로 돌린 뒤 사용자가 다시 취소했다. 상태는 같고 updated_at만 다르다(ABA)
                touch(order.getId(), retriedAt);
                return refundLookup(TossPaymentStatus.DONE);
            });

            recoveryService.recover(order.getId(), now);

            assertThat(current(order).getStatus()).isEqualTo(OrderStatus.REFUND_IN_PROGRESS);
            assertThat(current(order).getUpdatedAt()).isEqualTo(retriedAt);
        }
    }

    @Nested
    @DisplayName("스케줄러")
    @ExtendWith(OutputCaptureExtension.class)
    class Scheduler {

        // 경보 경계 시각이 인스턴스 상태라 테스트마다 새로 만든다
        private PaymentRecoveryScheduler scheduler;

        @BeforeEach
        void newScheduler() {
            scheduler = new PaymentRecoveryScheduler(recoveryService);
        }

        @Test
        @DisplayName("대상 구간 — 5분 ~ 1시간 전만, 오래된 순")
        void targetWindow() {
            stuckOrder("pk-recent", 4);
            Order newer = stuckOrder("pk-newer", 6);
            Order older = stuckOrder("pk-older", 50);
            stuckOrder("pk-abandoned", 61);

            assertThat(recoveryService.findTargetIds(now)).containsExactly(older.getId(), newer.getId());
        }

        @Test
        @DisplayName("대상·경보에 환불 진행 중 주문도 들어간다 (12단계)")
        void refundOrdersAreTargetedAndAlerted(CapturedOutput output) {
            Order payment = stuckOrder("pk-pay", 20);
            Order refund = refundingOrder("pk-refund", 10);
            Order abandonedRefund = refundingOrder("pk-refund-old", 70);

            assertThat(recoveryService.findTargetIds(now)).containsExactly(payment.getId(), refund.getId());

            int start = output.getOut().length();
            scheduler.alertAbandoned(now);
            assertThat(output.getOut().substring(start)).contains("count=1").contains(abandonedRefund.getOrderNumber());
        }

        @Test
        @DisplayName("실행 예산 초과 — 남은 주문은 다음 주기로 넘긴다")
        void budgetStopsBeforeNextOrder() {
            stuckOrder("pk-a", 20);
            stuckOrder("pk-b", 10);
            gateway.onLookup(() -> {
                sleep(200);
                throw new PaymentResultUnknownException(null, false, "느림", null);
            });

            scheduler.recoverStuckOrders(now, Duration.ofMillis(100));

            assertThat(gateway.calls()).containsExactly("getPayment:pk-a");
        }

        @Test
        @DisplayName("한 건의 예외가 나머지를 막지 않는다")
        void oneFailureDoesNotStopOthers() {
            stuckOrder("pk-a", 20);
            Order second = stuckOrder("pk-b", 10);
            AtomicInteger count = new AtomicInteger();
            gateway.onLookup(() -> {
                if (count.getAndIncrement() == 0) {
                    throw new IllegalStateException("예상 못 한 실패");
                }
                return lookup("pk-b", second.getOrderNumber(), TossPaymentStatus.ABORTED, PRICE);
            });

            scheduler.recoverStuckOrders(now, Duration.ofSeconds(50));

            assertThat(current(second).getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("1시간 경보 — 첫 실행은 전체, 다음 실행은 새로 경계를 넘은 주문만")
        void alertOnlyOnce(CapturedOutput output) {
            Order old1 = stuckOrder("pk-old1", 90);
            Order old2 = stuckOrder("pk-old2", 61);
            Order crossing = stuckOrder("pk-crossing", 59);

            int start = output.getOut().length();
            scheduler.alertAbandoned(now);
            String first = output.getOut().substring(start);

            assertThat(first).contains("count=2").contains(old1.getOrderNumber()).contains(old2.getOrderNumber())
                    .doesNotContain(crossing.getOrderNumber());

            int mark = output.getOut().length();
            scheduler.alertAbandoned(now.plusMinutes(2));
            String second = output.getOut().substring(mark);

            assertThat(second).contains("count=1").contains(crossing.getOrderNumber())
                    .doesNotContain(old1.getOrderNumber());
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

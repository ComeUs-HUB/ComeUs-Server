package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.entity.ShippingAddress;
import com.toyproject.backend.domain.order.exception.OrderErrorCode;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.order.service.OrderCancelService;
import com.toyproject.backend.domain.order.service.OrderService;
import com.toyproject.backend.domain.payment.entity.Payment;
import com.toyproject.backend.domain.payment.entity.PaymentStatus;
import com.toyproject.backend.domain.payment.exception.PaymentErrorCode;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGateway;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGatewayConfig;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import com.toyproject.backend.domain.payment.repository.PaymentRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 4 · 12단계] 환불의 선점·반영 CAS, 토스 취소 결과별 처리, 회원·ADMIN 진입점을 실제 트랜잭션으로 검증한다.
 *
 * <p>토스만 {@link FakePaymentGateway}로 바꾼다. 주문은 승인 반영 메서드로 {@code PAID}까지 만든다.
 */
@SpringBootTest
@Import(FakePaymentGatewayConfig.class)
@DisplayName("RefundService 환불 (Phase 4 · 12단계)")
class RefundServiceTest {

    private static final ShippingAddress ADDRESS = ShippingAddress.of("구매자", "010-0000-0000", "서울시");
    private static final BigDecimal PRICE = new BigDecimal("19900");
    private static final int STOCK = 10;
    private static final OffsetDateTime CANCELED_AT = OffsetDateTime.parse("2026-11-17T11:00:00+09:00");

    @Autowired private RefundService refundService;
    @Autowired private RefundTransitions refundTransitions;
    @Autowired private PaymentTransitions paymentTransitions;
    @Autowired private OrderCancelService orderCancelService;
    @Autowired private OrderService orderService;
    @Autowired private FakePaymentGateway gateway;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long buyer;
    private Long productId;
    private Order order;

    @BeforeEach
    void setUp() {
        gateway.reset();
        buyer = userRepository.save(User.ofKakao("kakao-refund", "buyer@example.com", "구매자")).getId();
        Category root = categoryRepository.save(Category.root("상의", 1));
        Category child = categoryRepository.save(Category.child(root, "티셔츠", 1));
        productId = productRepository.save(
                Product.create(child, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), STOCK)).getId();
        order = paidOrder("pk-1");
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

    /** 승인 경로의 선점·반영을 그대로 거쳐 결제 1건이 있는 PAID 주문을 만든다. 재고는 건드리지 않는다. */
    private Order paidOrder(String paymentKey) {
        Order created = transactionTemplate.execute(status -> {
            Order o = Order.create(userRepository.getReferenceById(buyer), UUID.randomUUID().toString(), ADDRESS);
            o.addItem(productRepository.getReferenceById(productId), 1);
            return orderRepository.save(o);
        });
        transactionTemplate.executeWithoutResult(status -> orderRepository.claimPayment(created.getId(), paymentKey,
                OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS, LocalDateTime.now()));
        TossPayment approved = new TossPayment(paymentKey, created.getOrderNumber(), TossPaymentStatus.DONE, PRICE,
                "카드", OffsetDateTime.parse("2026-11-17T10:00:00+09:00"), null);
        assertThat(paymentTransitions.applySuccess(created.getId(), created.getOrderNumber(), paymentKey, approved))
                .isPresent();
        return created;
    }

    private TossPayment canceled() {
        return cancelResponse(TossPaymentStatus.CANCELED, List.of(new TossPayment.Cancel(CANCELED_AT)));
    }

    private TossPayment cancelResponse(TossPaymentStatus status, List<TossPayment.Cancel> cancels) {
        return new TossPayment("pk-1", order.getOrderNumber(), status, PRICE, "카드",
                OffsetDateTime.parse("2026-11-17T10:00:00+09:00"), cancels);
    }

    private OrderStatus status() {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private Payment payment() {
        return paymentRepository.findByOrderId(order.getId()).orElseThrow();
    }

    private int stock() {
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }

    private void forceStatus(OrderStatus to) {
        jdbcTemplate.update("update orders set status = ? where id = ?", to.name(), order.getId());
    }

    private static ErrorCode errorCodeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    private void assertNotRefunded(OrderStatus expectedStatus) {
        assertThat(status()).isEqualTo(expectedStatus);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment().getRefundedAt()).isNull();
        assertThat(stock()).as("토스 취소가 확인되기 전에는 재고를 풀지 않는다").isEqualTo(STOCK);
    }

    @Test
    @DisplayName("환불 성공 — CANCELLED, 결제 REFUNDED, 환불 시각은 토스 값, 재고 복구")
    void refundSuccess() {
        gateway.onCancel(this::canceled);

        refundService.refund(order.getId(), "고객 요청");

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment().getRefundedAt())
                .isEqualTo(CANCELED_AT.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        assertThat(stock()).isEqualTo(STOCK + 1);
        assertThat(gateway.calls()).containsExactly("cancel:pk-1");
    }

    @Test
    @DisplayName("응답에 취소 시각이 없음 — 반영 시각으로 저장하고 환불은 성립한다")
    void missingCanceledAtFallsBackToNow() {
        gateway.onCancel(() -> cancelResponse(TossPaymentStatus.CANCELED, null));

        refundService.refund(order.getId(), "고객 요청");

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment().getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("확정 거절 — PAY007, PAID로 복귀, 재고 그대로")
    void rejectedReturnsToPaid() {
        gateway.onCancel(() -> { throw new PaymentRejectedException("REFUND_REJECTED", "거절"); });

        assertThatThrownBy(() -> refundService.refund(order.getId(), "고객 요청"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_REFUND_REJECTED));

        assertNotRefunded(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결과 불명 — PAY008, REFUND_IN_PROGRESS 유지")
    void unknownKeepsInProgress() {
        gateway.onCancel(() -> { throw new PaymentResultUnknownException(null, false, "타임아웃", null); });

        assertThatThrownBy(() -> refundService.refund(order.getId(), "고객 요청"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS));

        assertNotRefunded(OrderStatus.REFUND_IN_PROGRESS);
    }

    @Test
    @DisplayName("응답 상태가 CANCELED가 아님 — PAY008, 반영하지 않는다")
    void unexpectedResponseIsInProgress() {
        gateway.onCancel(() -> cancelResponse(TossPaymentStatus.PARTIAL_CANCELED, List.of(new TossPayment.Cancel(CANCELED_AT))));

        assertThatThrownBy(() -> refundService.refund(order.getId(), "고객 요청"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS));

        assertNotRefunded(OrderStatus.REFUND_IN_PROGRESS);
    }

    @Test
    @DisplayName("반영 중 예외 — PAY008, 롤백돼 REFUND_IN_PROGRESS 유지")
    void applyFailureIsInProgress() {
        // 결제가 이미 환불 상태면 Payment.refund()가 예외를 던져 반영 트랜잭션 전체가 롤백된다
        jdbcTemplate.update("update payments set status = 'REFUNDED' where order_id = ?", order.getId());
        gateway.onCancel(this::canceled);

        assertThatThrownBy(() -> refundService.refund(order.getId(), "고객 요청"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS));

        assertThat(status()).isEqualTo(OrderStatus.REFUND_IN_PROGRESS);
        assertThat(stock()).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("반영 0건이지만 배치가 먼저 CANCELLED로 반영 — 성공으로 끝나고 재고는 한 번만 복구")
    void appliedFirstByBatchIsSuccess() {
        gateway.onCancel(() -> {
            refundTransitions.applySuccess(order.getId(), canceled());   // 응답 전 배치가 먼저 반영했다
            return canceled();
        });

        refundService.refund(order.getId(), "고객 요청");

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stock()).isEqualTo(STOCK + 1);
    }

    @Test
    @DisplayName("환불 중인 주문 — O003, 토스 호출 없음")
    void refundInProgressIsRejected() {
        forceStatus(OrderStatus.REFUND_IN_PROGRESS);

        assertThatThrownBy(() -> orderCancelService.cancelByMember(buyer, order.getOrderNumber()))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.ORDER_PAYMENT_IN_PROGRESS));
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    @DisplayName("선점 실패 — 진입점이 PAID로 읽은 뒤 배송됐으면 O002, 토스 호출 없음")
    void claimFailureUsesCurrentStatus() {
        forceStatus(OrderStatus.SHIPPING);

        assertThatThrownBy(() -> refundService.refund(order.getId(), "고객 요청"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.ORDER_CANCEL_NOT_ALLOWED));
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    @DisplayName("환불 선점 중에는 ADMIN 배송 처리가 O005로 막힌다")
    void shippingBlockedWhileRefunding() {
        gateway.onCancel(() -> {
            assertThatThrownBy(() -> orderService.changeOrderStatus(order.getId(), OrderStatus.SHIPPING))
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION));
            return canceled();
        });

        refundService.refund(order.getId(), "고객 요청");

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("회원 취소 진입점 — PAID면 환불한다")
    void memberCancelOfPaidRefunds() {
        gateway.onCancel(this::canceled);

        orderCancelService.cancelByMember(buyer, order.getOrderNumber());

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("ADMIN 취소 진입점 — PAID면 환불한다")
    void adminCancelOfPaidRefunds() {
        gateway.onCancel(this::canceled);

        orderCancelService.cancelByAdmin(order.getId());

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stock()).isEqualTo(STOCK + 1);
    }

    @Test
    @DisplayName("동시 취소 — 토스 취소는 한 번, 재고 복구도 한 번")
    void concurrentCancelCallsTossOnce() throws Exception {
        gateway.onCancel(() -> {
            sleep(200);
            return canceled();
        });
        Callable<Void> request = () -> {
            orderCancelService.cancelByMember(buyer, order.getOrderNumber());
            return null;
        };

        int succeeded = 0;
        for (Future<Void> future : runConcurrently(List.of(request, request))) {
            try {
                future.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(errorCodeOf(e.getCause())).isIn(
                        OrderErrorCode.ORDER_PAYMENT_IN_PROGRESS, OrderErrorCode.ORDER_ALREADY_CANCELLED);
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(gateway.calls()).containsExactly("cancel:pk-1");
        assertThat(stock()).isEqualTo(STOCK + 1);
    }

    private static <T> List<Future<T>> runConcurrently(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream().map(task -> executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            })).toList();
            ready.await();
            start.countDown();
            for (Future<T> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                } catch (ExecutionException | java.util.concurrent.TimeoutException ignored) {
                    // 결과는 호출자가 본다
                }
            }
            return futures;
        } finally {
            executor.shutdown();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

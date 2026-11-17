package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.entity.ShippingAddress;
import com.toyproject.backend.domain.order.exception.OrderErrorCode;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.payment.dto.PaymentConfirmRequest;
import com.toyproject.backend.domain.payment.dto.PaymentResponse;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 4 · 10단계] 결제 승인의 선점·반영 CAS와 중복 요청을 실제 트랜잭션으로 검증한다.
 *
 * <p>토스만 {@link FakePaymentGateway}로 바꾼다. 클래스에 {@code @Transactional}을 두지 않는다 —
 * 선점과 반영이 각자 커밋돼야 CAS를 검증할 수 있다.
 */
@SpringBootTest
@Import(FakePaymentGatewayConfig.class)
@DisplayName("PaymentService 결제 승인")
class PaymentServiceTest {

    private static final ShippingAddress ADDRESS = ShippingAddress.of("구매자", "010-0000-0000", "서울시");
    private static final BigDecimal PRICE = new BigDecimal("19900");

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentTransitions transitions;
    @Autowired private FakePaymentGateway gateway;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long buyer;
    private Order order;

    @BeforeEach
    void setUp() {
        gateway.reset();
        buyer = userRepository.save(User.ofKakao("kakao-pay-1", "buyer@example.com", "구매자")).getId();
        Category root = categoryRepository.save(Category.root("상의", 1));
        Category child = categoryRepository.save(Category.child(root, "티셔츠", 1));
        Product tee = productRepository.save(Product.create(child, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), 10));
        order = transactionTemplate.execute(status -> {
            Order created = Order.create(userRepository.getReferenceById(buyer), UUID.randomUUID().toString(), ADDRESS);
            created.addItem(productRepository.getReferenceById(tee.getId()), 1);
            return orderRepository.save(created);
        });
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

    private PaymentResponse confirm(String paymentKey) {
        return confirm(buyer, paymentKey, PRICE);
    }

    private PaymentResponse confirm(Long userId, String paymentKey, BigDecimal amount) {
        return paymentService.confirm(userId, new PaymentConfirmRequest(paymentKey, order.getOrderNumber(), amount));
    }

    private TossPayment approved(String paymentKey) {
        return approved(paymentKey, PRICE);
    }

    private TossPayment approved(String paymentKey, BigDecimal amount) {
        return new TossPayment(paymentKey, order.getOrderNumber(), TossPaymentStatus.DONE, amount, "카드",
                OffsetDateTime.parse("2026-11-14T10:15:30+09:00"), null);
    }

    private Order current() {
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    private void transition(OrderStatus from, OrderStatus to) {
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.transitionStatus(order.getId(), from, to, LocalDateTime.now()));
    }

    private static ErrorCode errorCodeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("승인 성공 — PAID와 결제 1건, 응답은 토스 값")
    void confirmSuccess() {
        gateway.onConfirm(() -> approved("pk-1"));

        PaymentResponse response = confirm("pk-1");

        assertThat(response.orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(response.amount()).isEqualByComparingTo(PRICE);
        assertThat(response.method()).isEqualTo("카드");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.paidAt()).isNotNull();
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(gateway.calls()).containsExactly("confirm:pk-1");
    }

    @Test
    @DisplayName("같은 paymentKey 재요청 — 토스를 다시 부르지 않고 같은 응답을 돌려준다")
    void sameKeyRetryReturnsExistingPayment() {
        gateway.onConfirm(() -> approved("pk-1"));
        PaymentResponse first = confirm("pk-1");

        PaymentResponse retry = confirm("pk-1");

        assertThat(retry).as("응답을 잃은 FE의 재시도가 같은 값을 받아야 한다").isEqualTo(first);
        assertThat(gateway.calls()).hasSize(1);
    }

    @Test
    @DisplayName("다른 paymentKey로 결제된 주문 — PAY002, 토스 호출 없음")
    void paidWithOtherKeyIsAlreadyExists() {
        gateway.onConfirm(() -> approved("pk-1"));
        confirm("pk-1");

        assertThatThrownBy(() -> confirm("pk-2"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_EXISTS));
        assertThat(gateway.calls()).containsExactly("confirm:pk-1");
    }

    @Test
    @DisplayName("승인 진행 중인 주문 — PAY003, 토스 호출 없음")
    void inProgressOrderIsPaymentInProgress() {
        transactionTemplate.executeWithoutResult(status -> orderRepository.claimPayment(order.getId(), "pk-other",
                OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS, LocalDateTime.now()));

        assertThatThrownBy(() -> confirm("pk-1"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS));
        assertThat(gateway.calls()).isEmpty();
        assertThat(current().getPaymentKey()).as("선점한 시도의 키는 그대로다").isEqualTo("pk-other");
    }

    @Test
    @DisplayName("금액 불일치 — PAY004, 토스 호출 없음, 주문은 PENDING")
    void amountMismatchDoesNotCallToss() {
        assertThatThrownBy(() -> confirm(buyer, "pk-1", new BigDecimal("100")))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH));
        assertThat(gateway.calls()).isEmpty();
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(current().getPaymentKey()).isNull();
    }

    @Test
    @DisplayName("취소된 주문 — PAY005")
    void cancelledOrderIsNotAllowed() {
        transition(OrderStatus.PENDING, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> confirm("pk-1"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_NOT_ALLOWED));
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    @DisplayName("타인의 주문·없는 주문 — O001")
    void otherUsersOrderIsNotFound() {
        Long stranger = userRepository.save(User.ofKakao("kakao-pay-2", "other@example.com", "타인")).getId();

        assertThatThrownBy(() -> confirm(stranger, "pk-1", PRICE))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
        assertThatThrownBy(() -> paymentService.confirm(buyer, new PaymentConfirmRequest("pk-1", "ORD-NONE", PRICE)))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    @DisplayName("확정 거절 — PAY006, PENDING으로 돌아가 새 paymentKey로 다시 승인할 수 있다")
    void rejectionReturnsToPendingAndAllowsRetry() {
        gateway.onConfirm(() -> { throw new PaymentRejectedException("REJECT_CARD_COMPANY", "카드사 거절"); });

        assertThatThrownBy(() -> confirm("pk-1"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_REJECTED));
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(current().getPaymentKey()).isNull();
        assertThat(paymentRepository.findAll()).as("거절은 행으로 남기지 않는다").isEmpty();

        gateway.onConfirm(() -> approved("pk-2"));
        assertThat(confirm("pk-2").status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결과 불명 — PAY003, PAYMENT_IN_PROGRESS와 paymentKey를 남겨 배치가 판정한다")
    void unknownResultKeepsInProgress() {
        gateway.onConfirm(() -> { throw new PaymentResultUnknownException(null, false, "read timeout", null); });

        assertThatThrownBy(() -> confirm("pk-1"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS));
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(current().getPaymentKey()).as("복구 조회의 키다").isEqualTo("pk-1");
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("승인 응답의 금액이 주문과 다름 — PAY003, 전이하지 않는다")
    void mismatchedApprovalIsNotApplied() {
        gateway.onConfirm(() -> approved("pk-1", new BigDecimal("100")));

        assertThatThrownBy(() -> confirm("pk-1"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS));
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("토스 호출 중 다른 경로가 먼저 성공을 반영 — 결제는 1건, 요청은 기존 결제를 돌려준다")
    void otherPathAppliedFirst() {
        gateway.onConfirm(() -> {
            // 복구 배치가 같은 결과를 먼저 반영한 상황
            transitions.applySuccess(order.getId(), order.getOrderNumber(), "pk-1", approved("pk-1"));
            return approved("pk-1");
        });

        PaymentResponse response = confirm("pk-1");

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("이전 시도의 늦은 반영 — paymentKey가 달라 0건, 새 시도는 보존된다")
    void staleAttemptDoesNotOverwriteNewAttempt() {
        gateway.onConfirm(() -> {
            // 이 시도가 멈춘 사이 거절로 PENDING이 됐고, 새 시도가 pk-2로 선점했다
            transactionTemplate.executeWithoutResult(status -> {
                orderRepository.applyPaymentRejection(order.getId(), "pk-1",
                        OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PENDING, LocalDateTime.now());
                orderRepository.claimPayment(order.getId(), "pk-2",
                        OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS, LocalDateTime.now());
            });
            return approved("pk-1");
        });

        assertThatThrownBy(() -> confirm("pk-1"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS));
        assertThat(current().getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(current().getPaymentKey()).isEqualTo("pk-2");
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("같은 주문의 동시 승인 2건 — 토스는 한 번만 불리고, 한쪽은 성공·한쪽은 PAY003")
    void concurrentConfirmCallsTossOnce() throws Exception {
        CountDownLatch inCall = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        gateway.onConfirm(() -> {
            inCall.countDown();
            try {
                // 진 쪽이 끝날 때까지 토스 응답을 붙잡아 두 요청을 확실히 겹친다
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return approved("pk-1");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentResponse>> futures = List.of(
                executor.submit(() -> { start.await(); return confirm("pk-1"); }),
                executor.submit(() -> { start.await(); return confirm("pk-1"); }));
        start.countDown();

        assertThat(inCall.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.currentTimeMillis() + 10_000;
        while (futures.stream().noneMatch(Future::isDone) && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        release.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int success = 0;
        int inProgress = 0;
        for (Future<PaymentResponse> future : futures) {
            try {
                future.get();
                success++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(errorCodeOf(e.getCause())).isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS);
                inProgress++;
            }
        }
        assertThat(success).isEqualTo(1);
        assertThat(inProgress).isEqualTo(1);
        assertThat(gateway.calls()).as("승인 진입은 선점 CAS로 한 번만 허용된다").hasSize(1);
        assertThat(paymentRepository.findAll()).hasSize(1);
    }
}

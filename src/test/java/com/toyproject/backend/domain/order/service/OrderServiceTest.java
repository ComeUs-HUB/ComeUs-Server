package com.toyproject.backend.domain.order.service;

import com.toyproject.backend.domain.cart.entity.Cart;
import com.toyproject.backend.domain.cart.repository.CartRepository;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.order.dto.OrderCreateResponse;
import com.toyproject.backend.domain.order.dto.OrderDetailResponse;
import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.entity.ShippingAddress;
import com.toyproject.backend.domain.order.exception.OrderErrorCode;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import com.toyproject.backend.domain.product.exception.ProductErrorCode;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.exception.CommonErrorCode;
import com.toyproject.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 4 · 5단계] 주문 생성의 재고·장바구니·멱등 규칙을 실제 트랜잭션으로 검증한다.
 *
 * <p>클래스에 {@code @Transactional}을 두지 않는다 — 롤백과 동시 요청을 보려면 커밋이 필요하다.
 * H2는 MySQL과 락이 달라 동시성 테스트는 낡은 재고 회귀만 잡는다. 교착 방지는 검증하지 않는다.
 */
@SpringBootTest
@DisplayName("OrderService")
class OrderServiceTest {

    private static final ShippingAddress ADDRESS = ShippingAddress.of("구매자", "010-0000-0000", "서울시");

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderExpirationScheduler expirationScheduler;

    private Category category;
    private int userSequence;

    @BeforeEach
    void setUp() {
        Category root = categoryRepository.save(Category.root("상의", 1));
        category = categoryRepository.save(Category.child(root, "티셔츠", 1));
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll(categoryRepository.findAll().stream()
                .filter(c -> c.getParent() != null).toList());
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Product product(String price, int stock) {
        return productRepository.save(Product.create(category, "기본 반팔티", "면 100%", new BigDecimal(price), stock));
    }

    private Long buyer() {
        int n = ++userSequence;
        return userRepository.save(User.ofKakao("kakao-" + n, "buyer" + n + "@example.com", "구매자")).getId();
    }

    /** @return 담은 순서대로의 항목 id */
    private List<Long> cartOf(Long userId, Product... products) {
        return transactionTemplate.execute(status -> {
            Cart cart = Cart.create(userRepository.findById(userId).orElseThrow());
            for (Product product : products) {
                cart.addItem(productRepository.findById(product.getId()).orElseThrow(), 1);
            }
            cartRepository.saveAndFlush(cart);
            return cart.getItems().stream().map(item -> item.getId()).toList();
        });
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
    }

    private List<Long> remainingItemsOf(Long userId) {
        return transactionTemplate.execute(status -> cartRepository.findByUserId(userId).orElseThrow()
                .getItems().stream().map(item -> item.getId()).toList());
    }

    private static ErrorCode errorCodeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    /** 요청을 동시에 출발시키고 모두 끝날 때까지 기다린다. */
    private static <T> List<Future<T>> runConcurrently(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return futures;
    }

    @Test
    @DisplayName("선택한 항목만 주문되고 지워지며 그 재고만 줄어든다")
    void ordersOnlySelectedItems() {
        Product tee = product("19900.00", 5);
        Product shirt = product("49900.00", 5);
        Long userId = buyer();
        List<Long> items = cartOf(userId, tee, shirt);

        OrderCreateResponse response = orderService.createOrder(userId, "key-1", List.of(items.get(0)), ADDRESS);

        assertThat(response.amount()).isEqualByComparingTo("19900.00");
        assertThat(response.orderNumber()).matches("ORD-\\d{8}-[0-9A-F]{12}");
        assertThat(stockOf(tee)).isEqualTo(4);
        assertThat(stockOf(shirt)).isEqualTo(5);
        assertThat(remainingItemsOf(userId)).containsExactly(items.get(1));
    }

    @Test
    @DisplayName("재고가 부족하면 앞 항목의 차감까지 되돌리고 장바구니를 남긴다")
    void outOfStockRollsBackEverything() {
        Product enough = product("19900.00", 5);
        Product scarce = product("49900.00", 1);
        Long userId = buyer();
        List<Long> items = cartOf(userId, enough, scarce);
        scarce = productRepository.findById(scarce.getId()).orElseThrow();
        scarce.decreaseStock(1);
        productRepository.save(scarce);

        assertThatThrownBy(() -> orderService.createOrder(userId, "key-1", items, ADDRESS))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(ProductErrorCode.OUT_OF_STOCK));

        assertThat(stockOf(enough)).isEqualTo(5);
        assertThat(remainingItemsOf(userId)).containsExactlyElementsOf(items);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("담은 뒤 숨겨진 상품은 주문되지 않는다")
    void hiddenProductIsRejected() {
        Product tee = product("19900.00", 5);
        Long userId = buyer();
        List<Long> items = cartOf(userId, tee);
        tee = productRepository.findById(tee.getId()).orElseThrow();
        tee.hide();
        productRepository.save(tee);

        assertThatThrownBy(() -> orderService.createOrder(userId, "key-1", items, ADDRESS))
                .as("decreaseStock은 숨김을 거르지 않으므로 이 확인이 없으면 주문된다")
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));

        assertThat(stockOf(tee)).isEqualTo(5);
    }

    @Test
    @DisplayName("빈 목록은 C001이고 0원 주문이 생기지 않는다")
    void emptySelectionIsRejected() {
        Long userId = buyer();
        cartOf(userId, product("19900.00", 5));

        assertThatThrownBy(() -> orderService.createOrder(userId, "key-1", List.of(), ADDRESS))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("동시 주문은 재고만큼만 성공하고 음수가 되지 않는다")
    void concurrentOrdersNeverOversell() throws Exception {
        Product tee = product("19900.00", 5);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Long userId = buyer();
            List<Long> items = cartOf(userId, tee);
            tasks.add(() -> orderService.createOrder(userId, UUID.randomUUID().toString(), items, ADDRESS).orderNumber());
        }

        int succeeded = 0;
        int outOfStock = 0;
        for (Future<String> future : runConcurrently(tasks)) {
            try {
                future.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(errorCodeOf(e.getCause())).isEqualTo(ProductErrorCode.OUT_OF_STOCK);
                outOfStock++;
            }
        }

        assertThat(succeeded).isEqualTo(5);
        assertThat(outOfStock).isEqualTo(7);
        assertThat(stockOf(tee)).isZero();
        assertThat(productRepository.findById(tee.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("같은 멱등키의 동시 요청은 주문 하나로 끝난다")
    void concurrentSameKeyCreatesOneOrder() throws Exception {
        Product tee = product("19900.00", 5);
        Long userId = buyer();
        List<Long> items = cartOf(userId, tee);
        Callable<String> request = () -> orderService.createOrder(userId, "key-1", items, ADDRESS).orderNumber();

        List<Future<String>> futures = runConcurrently(List.of(request, request, request));

        assertThat(futures.get(0).get()).isEqualTo(futures.get(1).get()).isEqualTo(futures.get(2).get());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(tee)).isEqualTo(4);
    }

    private Long orderIdOf(String orderNumber) {
        return orderRepository.findAll().stream()
                .filter(order -> order.getOrderNumber().equals(orderNumber))
                .findFirst().orElseThrow().getId();
    }

    private Order orderOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private void forceStatus(Long orderId, OrderStatus from, OrderStatus to) {
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.transitionStatus(orderId, from, to, LocalDateTime.now()));
    }

    @Test
    @DisplayName("상세는 트랜잭션 밖에서 항목과 주문 시점 상품명·가격을 읽어 준다")
    void detailReadsItemsWithSnapshotPrice() {
        Product tee = product("19900.00", 5);
        Long userId = buyer();
        String number = orderService.createOrder(userId, "key-1", cartOf(userId, tee), ADDRESS).orderNumber();
        tee = productRepository.findById(tee.getId()).orElseThrow();
        tee.updateInfo(category, "이름 변경", "면 100%", new BigDecimal("29900.00"), 4);
        productRepository.save(tee);

        OrderDetailResponse detail = orderService.getMyOrderDetail(userId, orderIdOf(number));

        assertThat(detail.items()).singleElement().satisfies(item -> {
            assertThat(item.priceAtOrder()).isEqualByComparingTo("19900.00");
            assertThat(item.productName()).isEqualTo("기본 반팔티");
        });
    }

    @Test
    @DisplayName("취소하면 모든 항목의 재고가 돌아오고 updatedAt이 갱신된다")
    void cancelRestoresStock() {
        Product soldOut = product("19900.00", 1);
        Product tee = product("49900.00", 5);
        Long userId = buyer();
        String number = orderService.createOrder(userId, "key-1", cartOf(userId, soldOut, tee), ADDRESS).orderNumber();
        Long orderId = orderIdOf(number);
        LocalDateTime before = orderOf(orderId).getUpdatedAt();

        orderService.cancelOrder(userId, orderId);

        assertThat(orderOf(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderOf(orderId).getUpdatedAt())
                .as("벌크 UPDATE는 @LastModifiedDate를 거치지 않는다").isAfter(before);
        assertThat(stockOf(soldOut)).isEqualTo(1);
        assertThat(stockOf(tee)).isEqualTo(5);
        assertThat(productRepository.findById(soldOut.getId()).orElseThrow().getStatus())
                .isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("PENDING이 아니면 상태별 코드로 거절하고 재고를 건드리지 않는다")
    void cancelRejectedByStatus() {
        Product tee = product("19900.00", 5);
        Long userId = buyer();
        String number = orderService.createOrder(userId, "key-1", cartOf(userId, tee), ADDRESS).orderNumber();
        Long orderId = orderIdOf(number);

        forceStatus(orderId, OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS);
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.ORDER_PAYMENT_IN_PROGRESS));

        forceStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PAID);
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .as("환불 없이 재고만 돌아가면 안 된다")
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.ORDER_CANCEL_NOT_ALLOWED));

        forceStatus(orderId, OrderStatus.PAID, OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(OrderErrorCode.ORDER_ALREADY_CANCELLED));

        assertThat(stockOf(tee)).isEqualTo(4);
    }

    @Test
    @DisplayName("동시 취소는 재고를 한 번만 복구한다")
    void concurrentCancelRestoresOnce() throws Exception {
        Product tee = product("19900.00", 5);
        Long userId = buyer();
        String number = orderService.createOrder(userId, "key-1", cartOf(userId, tee), ADDRESS).orderNumber();
        Long orderId = orderIdOf(number);
        Callable<Boolean> request = () -> {
            orderService.cancelOrder(userId, orderId);
            return true;
        };

        int succeeded = 0;
        for (Future<Boolean> future : runConcurrently(List.of(request, request, request))) {
            try {
                future.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(errorCodeOf(e.getCause())).isEqualTo(OrderErrorCode.ORDER_ALREADY_CANCELLED);
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(stockOf(tee)).isEqualTo(5);
    }

    private void ageOrder(Long orderId, int minutes) {
        jdbcTemplate.update("update orders set updated_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(minutes), orderId);
    }

    @Test
    @DisplayName("15분 지난 PENDING만 취소되어 재고가 돌아오고 승인 중인 주문은 건드리지 않는다")
    void expiresOnlyOldPendingOrders() {
        Product tee = product("19900.00", 10);
        Long oldPending = placeOrder(tee);
        Long freshPending = placeOrder(tee);
        Long oldInProgress = placeOrder(tee);
        ageOrder(oldPending, 16);
        ageOrder(freshPending, 14);
        forceStatus(oldInProgress, OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS);
        ageOrder(oldInProgress, 16);

        expirationScheduler.expirePendingOrders();

        assertThat(orderOf(oldPending).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderOf(freshPending).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderOf(oldInProgress).getStatus())
                .as("토스 승인이 끝났을 수 있어 복구 배치만 판정한다 — ADR 0026").isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(stockOf(tee)).isEqualTo(8);
    }

    @Test
    @DisplayName("조회 뒤 승인 거절로 돌아온 주문은 시각이 새로워 취소되지 않는다")
    void refreshedPendingOrderIsNotExpired() {
        Product tee = product("19900.00", 10);
        Long orderId = placeOrder(tee);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        ageOrder(orderId, 16);
        ageOrder(orderId, 1);

        orderService.expirePendingOrder(orderId, cutoff);

        assertThat(orderOf(orderId).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockOf(tee)).isEqualTo(9);
    }

    @Test
    @DisplayName("동시에 돌아도 재고는 한 번만 복구된다")
    void concurrentExpirationRestoresOnce() throws Exception {
        Product tee = product("19900.00", 10);
        Long orderId = placeOrder(tee);
        ageOrder(orderId, 16);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        Callable<Boolean> expire = () -> {
            orderService.expirePendingOrder(orderId, cutoff);
            return true;
        };

        for (Future<Boolean> future : runConcurrently(List.of(expire, expire, expire))) {
            future.get();
        }

        assertThat(stockOf(tee)).isEqualTo(10);
    }

    private Long placeOrder(Product product) {
        Long userId = buyer();
        String number = orderService.createOrder(userId, UUID.randomUUID().toString(), cartOf(userId, product), ADDRESS)
                .orderNumber();
        return orderIdOf(number);
    }
}

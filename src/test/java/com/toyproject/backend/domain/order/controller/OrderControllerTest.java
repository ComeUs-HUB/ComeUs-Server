package com.toyproject.backend.domain.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGateway;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGatewayConfig;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import com.toyproject.backend.domain.payment.repository.PaymentRepository;
import com.toyproject.backend.domain.payment.service.PaymentTransitions;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Import;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.cart.dto.CartItemAddRequest;
import com.toyproject.backend.domain.cart.repository.CartRepository;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.order.dto.OrderCreateRequest;
import com.toyproject.backend.domain.order.dto.OrderStatusUpdateRequest;
import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [Phase 4 · 7단계] 주문 API의 입력 검증·권한·트랜잭션 경계를 검증한다.
 *
 * <p>클래스에 {@code @Transactional}을 두지 않는다 — 있으면 {@code open-in-view: false}의
 * 지연 로딩 오류가 가려지고 서비스의 격리 수준과 충돌한다.
 */
@SpringBootTest
@Import(FakePaymentGatewayConfig.class)
@AutoConfigureMockMvc
@DisplayName("주문 API (Phase 4 · 7단계)")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private FakePaymentGateway gateway;
    @Autowired private PaymentTransitions paymentTransitions;
    @Autowired private PaymentRepository paymentRepository;

    private String buyer;
    private String admin;
    private Product tee;

    @BeforeEach
    void setUp() {
        gateway.reset();
        buyer = bearerOf(userRepository.save(User.ofKakao("kakao-order-1", "buyer@kakao.com", "구매자")));
        User adminUser = User.ofKakao("kakao-order-2", "admin@kakao.com", "관리자");
        adminUser.promoteToAdmin();
        admin = bearerOf(userRepository.save(adminUser));

        Category tops = categoryRepository.save(Category.root("상의", 1));
        Category tshirt = categoryRepository.save(Category.child(tops, "티셔츠", 1));
        tee = productRepository.save(Product.create(tshirt, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), 10));
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll(categoryRepository.findAll().stream()
                .filter(category -> category.getParent() != null).toList());
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String bearerOf(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user);
    }

    private Long addToCart() throws Exception {
        String body = mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemAddRequest(tee.getId(), 2))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.items[0].cartItemId")).longValue();
    }

    private ResultActions createOrder(String idempotencyKey, List<Long> cartItemIds) throws Exception {
        var request = post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, buyer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new OrderCreateRequest(cartItemIds, "구매자", "010-0000-0000", "서울시")));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private Long placeOrder() throws Exception {
        createOrder(UUID.randomUUID().toString(), List.of(addToCart())).andExpect(status().isOk());
        return orderRepository.findAll().get(0).getId();
    }

    /** 승인 반영까지 거친 PAID 주문(수량 2, 결제 1건). [12단계] */
    private String paidOrder() throws Exception {
        Long orderId = placeOrder();
        String orderNumber = orderRepository.findById(orderId).orElseThrow().getOrderNumber();
        BigDecimal amount = orderRepository.findById(orderId).orElseThrow().getTotalAmount();
        transactionTemplate.executeWithoutResult(status -> orderRepository.claimPayment(orderId, "pk-1",
                OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS, LocalDateTime.now()));
        paymentTransitions.applySuccess(orderId, orderNumber, "pk-1", tossPayment(orderNumber, amount, TossPaymentStatus.DONE));
        return orderNumber;
    }

    private static TossPayment tossPayment(String orderNumber, BigDecimal amount, TossPaymentStatus status) {
        return new TossPayment("pk-1", orderNumber, status, amount, "카드",
                OffsetDateTime.parse("2026-11-17T10:00:00+09:00"),
                List.of(new TossPayment.Cancel(OffsetDateTime.parse("2026-11-17T11:00:00+09:00"))));
    }

    private ResultActions cancel(String orderNumber) throws Exception {
        return mockMvc.perform(post("/api/orders/" + orderNumber + "/cancel").header(HttpHeaders.AUTHORIZATION, buyer));
    }

    private OrderStatus statusOf(String orderNumber) {
        return orderRepository.findAll().stream().filter(o -> o.getOrderNumber().equals(orderNumber))
                .findFirst().orElseThrow().getStatus();
    }

    private ResultActions changeStatus(String token, Long orderId, OrderStatus next) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/status")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(next))));
    }

    private void forceStatus(Long orderId, OrderStatus from, OrderStatus to) {
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.transitionStatus(orderId, from, to, LocalDateTime.now()));
    }

    @Test
    @DisplayName("멱등키가 없거나 UUID가 아니면 500이 아니라 C001이다")
    void invalidIdempotencyKeyIsBadRequest() throws Exception {
        createOrder(null, List.of(1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        createOrder("not-a-uuid", List.of(1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("주문부터 상세·목록·취소까지 트랜잭션 밖에서 지연 로딩 오류 없이 끝난다")
    void orderLifecycleOverHttp() throws Exception {
        String orderNumber = orderRepository.findById(placeOrder()).orElseThrow().getOrderNumber();

        mockMvc.perform(get("/api/orders/" + orderNumber).header(HttpHeaders.AUTHORIZATION, buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].productName").value("기본 반팔티"))
                .andExpect(jsonPath("$.data.items[0].productId").value(tee.getId()));

        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist());

        mockMvc.perform(post("/api/orders/" + orderNumber + "/cancel").header(HttpHeaders.AUTHORIZATION, buyer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderNumber).header(HttpHeaders.AUTHORIZATION, buyer))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("허용되지 않은 정렬 필드는 500이 아니라 C001이다")
    void unknownSortFieldIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/orders").param("sort", "paymentKey").header(HttpHeaders.AUTHORIZATION, buyer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("ADMIN이 아니면 관리 API를 호출할 수 없다")
    void adminApiRequiresAdminRole() throws Exception {
        Long orderId = placeOrder();

        mockMvc.perform(get("/api/admin/orders").header(HttpHeaders.AUTHORIZATION, buyer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("A002"));
        changeStatus(buyer, orderId, OrderStatus.CANCELLED)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 주문 목록은 상태 변경 API가 쓰는 PK를 orderNumber와 함께 내보낸다")
    void adminOrderListExposesId() throws Exception {
        Long orderId = placeOrder();
        String orderNumber = orderRepository.findById(orderId).orElseThrow().getOrderNumber();

        mockMvc.perform(get("/api/admin/orders").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(orderId))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(orderNumber));
    }

    @Test
    @DisplayName("ADMIN은 배송 전이만 진행할 수 있고 결제 전 주문을 배송으로 건너뛸 수 없다")
    void adminFollowsTransitionTable() throws Exception {
        Long orderId = placeOrder();

        changeStatus(admin, orderId, OrderStatus.SHIPPING)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("O005"));
        changeStatus(admin, orderId, OrderStatus.PAID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("O005"));

        forceStatus(orderId, OrderStatus.PENDING, OrderStatus.PAID);
        changeStatus(admin, orderId, OrderStatus.SHIPPING).andExpect(status().isOk());
        changeStatus(admin, orderId, OrderStatus.DELIVERED).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN도 결제 승인 중인 주문은 취소할 수 없고 PENDING 취소는 재고를 복구한다")
    void adminCancelRules() throws Exception {
        Long orderId = placeOrder();

        forceStatus(orderId, OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS);
        changeStatus(admin, orderId, OrderStatus.CANCELLED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("O003"));

        forceStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PENDING);
        changeStatus(admin, orderId, OrderStatus.CANCELLED).andExpect(status().isOk());

        assertThat(productRepository.findById(tee.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("PAID 주문 취소는 환불하고 재고를 복구한다 (12단계)")
    void cancelPaidOrderRefunds() throws Exception {
        String orderNumber = paidOrder();
        BigDecimal amount = orderRepository.findAll().get(0).getTotalAmount();
        gateway.onCancel(() -> tossPayment(orderNumber, amount, TossPaymentStatus.CANCELED));

        cancel(orderNumber).andExpect(status().isOk());

        assertThat(statusOf(orderNumber)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(productRepository.findById(tee.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("환불 확정 거절은 409 PAY007, 결과 불명은 409 PAY008 (12단계)")
    void refundFailureResponses() throws Exception {
        String orderNumber = paidOrder();

        gateway.onCancel(() -> { throw new PaymentRejectedException("REFUND_REJECTED", "거절"); });
        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAY007"));
        assertThat(statusOf(orderNumber)).isEqualTo(OrderStatus.PAID);

        gateway.onCancel(() -> { throw new PaymentResultUnknownException(null, false, "타임아웃", null); });
        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAY008"));
        assertThat(statusOf(orderNumber)).isEqualTo(OrderStatus.REFUND_IN_PROGRESS);

        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("O003"));
    }

    @Test
    @DisplayName("ADMIN도 PAID 주문을 취소하면 환불한다 (12단계)")
    void adminCancelPaidOrderRefunds() throws Exception {
        String orderNumber = paidOrder();
        Long orderId = orderRepository.findAll().get(0).getId();
        BigDecimal amount = orderRepository.findAll().get(0).getTotalAmount();
        gateway.onCancel(() -> tossPayment(orderNumber, amount, TossPaymentStatus.CANCELED));

        changeStatus(admin, orderId, OrderStatus.CANCELLED).andExpect(status().isOk());

        assertThat(statusOf(orderNumber)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(gateway.calls()).containsExactly("cancel:pk-1");
    }
}

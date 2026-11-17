package com.toyproject.backend.domain.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.ShippingAddress;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.payment.dto.PaymentConfirmRequest;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGateway;
import com.toyproject.backend.domain.payment.gateway.FakePaymentGatewayConfig;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import com.toyproject.backend.domain.payment.repository.PaymentRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** [Phase 4 · 10단계] 결제 승인 API의 요청 검증, 인증, 응답 형식. 판정 규칙은 {@code PaymentServiceTest}가 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakePaymentGatewayConfig.class)
@DisplayName("결제 API (Phase 4 · 10단계)")
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private FakePaymentGateway gateway;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private String buyer;
    private String orderNumber;

    @BeforeEach
    void setUp() {
        gateway.reset();
        User user = userRepository.save(User.ofKakao("kakao-pay-api", "buyer@kakao.com", "구매자"));
        buyer = "Bearer " + jwtProvider.createAccessToken(user);
        Category tops = categoryRepository.save(Category.root("상의", 1));
        Category tshirt = categoryRepository.save(Category.child(tops, "티셔츠", 1));
        Product tee = productRepository.save(Product.create(tshirt, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), 10));
        orderNumber = transactionTemplate.execute(status -> {
            Order order = Order.create(userRepository.getReferenceById(user.getId()), UUID.randomUUID().toString(),
                    ShippingAddress.of("구매자", "010-0000-0000", "서울시"));
            order.addItem(productRepository.getReferenceById(tee.getId()), 1);
            return orderRepository.save(order).getOrderNumber();
        });
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll(categoryRepository.findAll().stream()
                .filter(category -> category.getParent() != null).toList());
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions confirm(String token, Object body) throws Exception {
        var request = post("/api/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, token);
        }
        return mockMvc.perform(request);
    }

    private TossPayment approved() {
        return new TossPayment("pk-1", orderNumber, TossPaymentStatus.DONE, new BigDecimal("19900"), "간편결제",
                OffsetDateTime.parse("2026-11-14T10:15:30+09:00"), null);
    }

    @Test
    @DisplayName("승인 성공 — 200, 결제 PK와 paymentKey는 내보내지 않는다")
    void confirmSuccess() throws Exception {
        gateway.onConfirm(this::approved);

        confirm(buyer, new PaymentConfirmRequest("pk-1", orderNumber, new BigDecimal("19900")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.amount").value(19900))
                .andExpect(jsonPath("$.data.method").value("간편결제"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paidAt").exists())
                .andExpect(jsonPath("$.data.paymentKey").doesNotExist())
                .andExpect(jsonPath("$.data.id").doesNotExist());
    }

    @Test
    @DisplayName("필수 값 누락·소수 금액 — C001, 토스 호출 없음")
    void invalidRequestIsBadRequest() throws Exception {
        confirm(buyer, new PaymentConfirmRequest(null, orderNumber, new BigDecimal("19900")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
        confirm(buyer, new PaymentConfirmRequest("pk-1", orderNumber, new BigDecimal("19900.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
        org.assertj.core.api.Assertions.assertThat(gateway.calls()).isEmpty();
    }

    @Test
    @DisplayName("토큰 없음 — 401")
    void unauthenticatedIsUnauthorized() throws Exception {
        confirm(null, new PaymentConfirmRequest("pk-1", orderNumber, new BigDecimal("19900")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("확정 거절 — 402 PAY006")
    void rejectionIsPaymentRequired() throws Exception {
        gateway.onConfirm(() -> { throw new PaymentRejectedException("REJECT_CARD_COMPANY", "카드사 거절"); });

        confirm(buyer, new PaymentConfirmRequest("pk-1", orderNumber, new BigDecimal("19900")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("PAY006"));
    }

    @Test
    @DisplayName("금액 불일치 — 400 PAY004")
    void amountMismatchIsBadRequest() throws Exception {
        confirm(buyer, new PaymentConfirmRequest("pk-1", orderNumber, new BigDecimal("100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAY004"));
    }
}

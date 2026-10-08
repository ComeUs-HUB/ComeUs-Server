package com.toyproject.backend.domain.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.cart.dto.CartItemAddRequest;
import com.toyproject.backend.domain.cart.dto.CartItemQuantityRequest;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [Phase 4 · 3단계] 장바구니 API의 계약 검증 — 상태 코드, 에러 코드, 응답 모양.
 *
 * <p>합산·재고·상한 판정은 {@code CartTest}, 조립·잠금 순서는 {@code CartServiceTest}가 소유한다.
 * 테스트 트랜잭션에 서비스가 참여하므로 격리 수준·락은 여기서 검증되지 않는다(ADR 0020).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("장바구니 API (Phase 4 · 3단계)")
class CartControllerTest {

    private static final String CART_URI = "/api/cart";
    private static final String ITEMS_URI = "/api/cart/items";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String bearer;
    private String otherBearer;
    private Product tee;
    private Product shirt;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.ofKakao("kakao-cart-1", "buyer@kakao.com", "구매자"));
        User other = userRepository.save(User.ofKakao("kakao-cart-2", "other@kakao.com", "다른 회원"));
        bearer = "Bearer " + jwtProvider.createAccessToken(user);
        otherBearer = "Bearer " + jwtProvider.createAccessToken(other);

        Category tops = categoryRepository.save(Category.root("상의", 1));
        Category tshirt = categoryRepository.save(Category.child(tops, "티셔츠", 1));
        tee = productRepository.save(Product.create(tshirt, "기본 반팔티", "면 100%", new BigDecimal("19900.00"), 10));
        shirt = productRepository.save(Product.create(tshirt, "Oxford 셔츠", "면 100%", new BigDecimal("49900.00"), 5));
    }

    private ResultActions add(String token, Long productId, Integer quantity) throws Exception {
        return mockMvc.perform(post(ITEMS_URI)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CartItemAddRequest(productId, quantity))));
    }

    private ResultActions changeQuantity(String token, Long itemId, Integer quantity) throws Exception {
        return mockMvc.perform(put(ITEMS_URI + "/" + itemId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CartItemQuantityRequest(quantity))));
    }

    private Long addAndGetItemId(Long productId, int quantity) throws Exception {
        String body = add(bearer, productId, quantity).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.items[0].cartItemId")).longValue();
    }

    @Test
    @DisplayName("담기는 201이 아니라 200이고, 새 항목의 id까지 담긴 전체 장바구니를 돌려준다")
    void addReturnsWholeCartWithItemId() throws Exception {
        add(bearer, tee.getId(), 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].cartItemId").isNumber())
                .andExpect(jsonPath("$.data.items[0].productId").value(tee.getId()))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.data.items[0].stockQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(39800.00));

        add(bearer, shirt.getId(), 1)
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].cartItemId").isNumber());
    }

    @Test
    @DisplayName("수량이 없거나 1 미만이거나 상한을 넘으면 C001이다")
    void invalidQuantityIsC001() throws Exception {
        add(bearer, tee.getId(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
        add(bearer, tee.getId(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
        add(bearer, tee.getId(), CartItemAddRequest.MAX_QUANTITY + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        Long itemId = addAndGetItemId(tee.getId(), 1);
        changeQuantity(bearer, itemId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("본문을 객체로 바꾸지 못하면 500이 아니라 C001이다 — 정수 범위 초과·문자열 수량·깨진 JSON")
    void unreadableBodyIsC001() throws Exception {
        for (String quantity : new String[]{"99999999999", "\"abc\""}) {
            postRaw("{\"productId\":" + tee.getId() + ",\"quantity\":" + quantity + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"))
                    .andExpect(jsonPath("$.error.errors[0].field").value("quantity"));
        }
        postRaw("{broken")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"))
                .andExpect(jsonPath("$.error.errors").doesNotExist());
    }

    private ResultActions postRaw(String body) throws Exception {
        return mockMvc.perform(post(ITEMS_URI)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("상한 이내여도 재고를 넘으면 CT002(409)다")
    void quantityWithinCapButOverStockIsCt002() throws Exception {
        add(bearer, shirt.getId(), 6)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CT002"));
    }

    @Test
    @DisplayName("숨긴 상품은 담을 수 없다 — P001(404)")
    void hiddenProductIsP001() throws Exception {
        tee.hide();

        add(bearer, tee.getId(), 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    @DisplayName("타인의 항목은 수정·삭제할 수 없다 — 403이 아니라 404(CT001)")
    void otherMembersItemIsCt001() throws Exception {
        Long itemId = addAndGetItemId(tee.getId(), 1);
        add(otherBearer, shirt.getId(), 1);

        changeQuantity(otherBearer, itemId, 2)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CT001"));
        mockMvc.perform(delete(ITEMS_URI + "/" + itemId).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CT001"));
    }

    @Test
    @DisplayName("항목 삭제와 비우기도 전체 장바구니를 돌려주고, 장바구니가 없어도 조회·비우기는 빈 응답이다")
    void deletesReturnWholeCart() throws Exception {
        mockMvc.perform(get(CART_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalPrice").value(0));

        Long itemId = addAndGetItemId(tee.getId(), 1);
        add(bearer, shirt.getId(), 1);

        mockMvc.perform(delete(ITEMS_URI + "/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(shirt.getId()));

        mockMvc.perform(delete(CART_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalQuantity").value(0));
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get(CART_URI))
                .andExpect(status().isUnauthorized());
    }
}

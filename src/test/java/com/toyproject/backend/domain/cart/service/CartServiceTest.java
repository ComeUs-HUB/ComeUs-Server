package com.toyproject.backend.domain.cart.service;

import com.toyproject.backend.domain.cart.dto.CartItemResponse;
import com.toyproject.backend.domain.cart.dto.CartResponse;
import com.toyproject.backend.domain.cart.entity.Cart;
import com.toyproject.backend.domain.cart.exception.CartErrorCode;
import com.toyproject.backend.domain.cart.repository.CartRepository;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.exception.ProductErrorCode;
import com.toyproject.backend.domain.product.service.ProductService;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.service.UserService;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

/**
 * [Phase 4 · 2단계] {@link CartService}의 조립·lazy 생성·잠금 순서 검증.
 *
 * <p>합산·재고·상한·{@code CT001} 판정은 {@code CartTest}가, 실제 SQL과 uk 제약은 {@code CartRepositoryTest}가 소유한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService")
class CartServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ITEM_ID = 100L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserService userService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private CartService cartService;

    private static User user() {
        User user = User.ofKakao("kakao-1", "buyer@example.com", "구매자");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private static Product product(long id, String price, int stock) {
        Category category = Category.child(Category.root("상의", 1), "티셔츠", 1);
        Product product = Product.create(category, "상품" + id, "설명", new BigDecimal(price), stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private static Cart cartWith(Product product, int quantity) {
        Cart cart = Cart.create(user());
        cart.addItem(product, quantity);
        ReflectionTestUtils.setField(cart.getItems().get(0), "id", ITEM_ID);
        return cart;
    }

    private void givenExistingCart(Cart cart) {
        given(cartRepository.existsByUserId(USER_ID)).willReturn(true);
        given(cartRepository.findByUserId(USER_ID)).willReturn(Optional.of(cart));
    }

    @Nested
    @DisplayName("담기")
    class AddItem {

        @Test
        @DisplayName("첫 담기는 장바구니를 만들어 저장하고, 없는 행은 잠그지 않는다")
        void firstAddCreatesCartWithoutLocking() {
            Product tee = product(10L, "19900", 10);
            given(productService.getVisibleProduct(10L)).willReturn(tee);
            given(cartRepository.existsByUserId(USER_ID)).willReturn(false);
            given(userService.getUser(USER_ID)).willReturn(user());

            CartResponse response = cartService.addItem(USER_ID, 10L, 2);

            ArgumentCaptor<Cart> saved = ArgumentCaptor.forClass(Cart.class);
            then(cartRepository).should().saveAndFlush(saved.capture());
            assertThat(saved.getValue().getItems())
                    .as("새 장바구니에 담은 항목이 함께 저장된다")
                    .singleElement()
                    .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(2));
            then(cartRepository).should(never()).lockByUserId(any());
            assertThat(response.totalQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("기존 장바구니는 잠근 뒤 읽고, 새로 저장하지 않는다")
        void existingCartIsLockedThenLoaded() {
            Product tee = product(10L, "19900", 10);
            given(productService.getVisibleProduct(10L)).willReturn(tee);
            givenExistingCart(cartWith(tee, 1));

            CartResponse response = cartService.addItem(USER_ID, 10L, 2);

            InOrder order = inOrder(cartRepository);
            order.verify(cartRepository).lockByUserId(USER_ID);
            order.verify(cartRepository).findByUserId(USER_ID);
            then(cartRepository).should(never()).saveAndFlush(any());
            then(userService).shouldHaveNoInteractions();
            assertThat(response.items())
                    .as("동일 상품은 합산된다")
                    .singleElement()
                    .extracting(CartItemResponse::quantity)
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("숨긴·없는 상품이면 P001이고 장바구니를 건드리지 않는다")
        void invisibleProductTouchesNoCart() {
            given(productService.getVisibleProduct(10L))
                    .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

            assertThatThrownBy(() -> cartService.addItem(USER_ID, 10L, 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
            // 상품을 먼저 읽지 않으면 빈 장바구니 행이 남는다(lazy 생성 계약)
            then(cartRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("장바구니가 없을 때")
    class NoCart {

        @Test
        @DisplayName("조회는 빈 응답이고 쓰기를 하지 않는다")
        void getMyCartReturnsEmptyWithoutWriting() {
            given(cartRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            CartResponse response = cartService.getMyCart(USER_ID);

            assertThat(response.items()).isEmpty();
            assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            then(cartRepository).should(never()).saveAndFlush(any());
            then(cartRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("수량 변경·삭제는 CT001, 비우기는 성공한다 — 어느 쪽도 잠그지 않는다")
        void writesOnMissingCart() {
            given(cartRepository.existsByUserId(USER_ID)).willReturn(false);

            assertThatThrownBy(() -> cartService.changeQuantity(USER_ID, ITEM_ID, 1))
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
            assertThatThrownBy(() -> cartService.removeItem(USER_ID, ITEM_ID))
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
            assertThat(cartService.clear(USER_ID).items()).isEmpty();

            // 없는 행에 FOR UPDATE를 걸면 gap lock끼리 교착한다
            then(cartRepository).should(never()).lockByUserId(any());
        }
    }

    @Test
    @DisplayName("응답 합계는 현재가 기준이며 구매 불가 항목도 포함한다")
    void totalsUseCurrentPriceIncludingUnpurchasable() {
        Product tee = product(10L, "19900", 10);
        Product shirt = product(20L, "49900", 5);
        Cart cart = cartWith(tee, 2);
        cart.addItem(shirt, 1);
        shirt.hide();
        given(cartRepository.findByUserId(USER_ID)).willReturn(Optional.of(cart));

        CartResponse response = cartService.getMyCart(USER_ID);

        assertThat(response.totalQuantity()).isEqualTo(3);
        assertThat(response.totalPrice())
                .as("19,900 × 2 + 49,900 × 1. 숨긴 상품도 빼지 않는다 — 서버는 항목을 자동 조정하지 않는다")
                .isEqualByComparingTo("89700");
        assertThat(response.items())
                .extracting(CartItemResponse::productId, CartItemResponse::purchasable)
                .containsExactly(
                        tuple(10L, true),
                        tuple(20L, false));
    }

    @Test
    @DisplayName("쓰기 메서드는 모두 READ COMMITTED다 — 새 쓰기 메서드도 빠뜨리지 않는다")
    void everyWriteRunsInReadCommitted() {
        List<Method> writes = Arrays.stream(CartService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .filter(method -> !method.getAnnotation(Transactional.class).readOnly())
                .toList();

        assertThat(writes)
                .as("담기·수량 변경·삭제·비우기")
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("addItem", "changeQuantity", "removeItem", "clear");
        assertThat(writes)
                .as("REPEATABLE READ면 락 뒤의 일반 SELECT가 락 이전 스냅샷을 읽어 Lost Update가 난다(ADR 0020)")
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class).isolation())
                        .isEqualTo(Isolation.READ_COMMITTED));
    }
}

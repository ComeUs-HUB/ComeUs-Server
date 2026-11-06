package com.toyproject.backend.domain.cart.service;

import com.toyproject.backend.domain.cart.dto.CartResponse;
import com.toyproject.backend.domain.cart.entity.Cart;
import com.toyproject.backend.domain.cart.exception.CartErrorCode;
import com.toyproject.backend.domain.cart.repository.CartRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.service.ProductService;
import com.toyproject.backend.domain.user.service.UserService;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * [Phase 4 · 2단계] 장바구니 서비스. 불변식은 {@link Cart}가 소유하고 여기서는 조회·조립·트랜잭션만 한다.
 *
 * <p>쓰기는 READ COMMITTED에서 장바구니 행을 잠가 같은 회원의 요청을 직렬화한다(ADR 0020).
 * 행이 없으면 잠그지 않고, 동시 첫 담기는 C005(409)로 끝난다.
 * 외부 트랜잭션 안에서 부르면 격리 수준이 무시된다 — 호출자도 READ COMMITTED여야 한다.
 * 회원·상품은 각 서비스를 거친다 — 두 Repository는 자기 패키지 밖으로 나가지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final UserService userService;
    private final ProductService productService;

    /** 장바구니가 없으면 빈 응답이다. 조회가 행을 만들지 않는다. */
    public CartResponse getMyCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(CartResponse::from)
                .orElseGet(CartResponse::empty);
    }

    /**
     * 상품 담기. 첫 담기에서 장바구니를 만든다.
     *
     * @throws BusinessException P001 · CT003 · CT002
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse addItem(Long userId, Long productId, int quantity) {
        // 상품을 먼저 읽어야 P001일 때 빈 장바구니 행이 남지 않는다
        Product product = productService.getVisibleProduct(productId);

        Optional<Cart> locked = findLockedCart(userId);
        if (locked.isPresent()) {
            Cart cart = locked.get();
            cart.addItem(product, quantity);
            // 새 항목의 id(cartItemId)는 flush에서 생긴다
            cartRepository.flush();
            return CartResponse.from(cart);
        }

        // 장바구니가 없으면 잠그지 않고 만든다
        Cart cart = Cart.create(userService.getUser(userId));
        cart.addItem(product, quantity);
        // 동시 첫 담기의 uk 위반을 커밋이 아니라 이 자리에서 드러낸다
        cartRepository.saveAndFlush(cart);
        return CartResponse.from(cart);
    }

    /** @throws BusinessException CT001 · CT002 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse changeQuantity(Long userId, Long itemId, int quantity) {
        Cart cart = getLockedCart(userId);
        cart.changeQuantity(itemId, quantity);
        return CartResponse.from(cart);
    }

    /** @throws BusinessException CT001 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = getLockedCart(userId);
        cart.removeItem(itemId);
        return CartResponse.from(cart);
    }

    /** 비어 있거나 장바구니가 없어도 실패하지 않는다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse clear(Long userId) {
        findLockedCart(userId).ifPresent(Cart::clear);
        // 전체 삭제는 빈 응답을 return
        return CartResponse.empty();
    }

    /**
     * [Phase 4 · 5단계] 주문 트랜잭션에서 장바구니를 잠근다. 상품을 잠그기 전이라 그래프로 읽지 않는다.
     *
     * @throws BusinessException 장바구니가 없으면 CT001
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Cart lockForOrder(Long userId) {
        if (!cartRepository.existsByUserId(userId)) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
        return cartRepository.lockByUserId(userId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    /** 장바구니가 없으면 항목도 없다 — CT001. */
    private Cart getLockedCart(Long userId) {
        return findLockedCart(userId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    /** 있을 때만 {@code carts} 행을 잠근 뒤 항목·상품을 그래프로 읽는다. */
    private Optional<Cart> findLockedCart(Long userId) {
        if (!cartRepository.existsByUserId(userId)) {
            return Optional.empty();
        }
        cartRepository.lockByUserId(userId);
        return cartRepository.findByUserId(userId);
    }
}

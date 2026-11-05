package com.toyproject.backend.domain.order.service;

import com.toyproject.backend.domain.cart.entity.Cart;
import com.toyproject.backend.domain.cart.entity.CartItem;
import com.toyproject.backend.domain.cart.service.CartService;
import com.toyproject.backend.domain.order.dto.OrderCreateResponse;
import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.entity.ShippingAddress;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.service.ProductService;
import com.toyproject.backend.domain.user.service.UserService;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * [Phase 4 · 5단계] 주문 서비스. 흐름은 {@code docs/domains/purchase/order.md}, 근거는 ADR 0024·0025·0027.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final UserService userService;

    /**
     * 선택한 장바구니 항목을 주문으로 복사하고 재고를 점유한다. 같은 멱등키의 재요청은 기존 주문을 돌려준다.
     *
     * @throws BusinessException C001 · CT001 · P001 · P002
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderCreateResponse create(Long userId, String idempotencyKey,
                                      List<Long> cartItemIds, ShippingAddress shippingAddress) {
        // 1. 빈 목록을 거른다. 요청 DTO와 별개로 막는다 — 통과하면 항목 없는 0원 주문이 저장된다
        if (cartItemIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        // 2. carts 행을 잠가 같은 회원의 요청을 줄 세운다. 장바구니가 없으면 CT001
        Cart cart = cartService.lockForOrder(userId);

        // 3. 잠금 뒤에 멱등키를 조회한다. 이미 처리된 요청이면 기존 주문을 돌려주고 끝낸다
        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return OrderCreateResponse.from(existing.get());
        }

        // 4. 선택한 항목을 고른다. 없거나 타인의 항목이면 CT001
        List<CartItem> items = cart.selectItems(cartItemIds);

        // 5. 상품을 id 순으로 잠근다. 숨긴 상품이 있으면 P001
        // 여기까지 상품 상태를 읽지 않는다. 먼저 읽으면 잠금 쿼리가 낡은 재고를 덮어쓰지 못한다
        productService.lockVisibleProducts(items.stream().map(item -> item.getProduct().getId()).toList());

        // 6. 주문번호를 발급하고 배송지를 스냅샷한 PENDING 주문을 만든다
        Order order = Order.create(userService.getUser(userId), idempotencyKey, shippingAddress);

        // 7. 항목마다 재고를 차감하고 가격을 스냅샷한다. 재고가 부족하면 P002로 전부 롤백된다
        for (CartItem item : items) {
            Product product = item.getProduct();
            product.decreaseStock(item.getQuantity());
            order.addItem(product, item.getQuantity());
        }

        // 8. 주문과 항목을 저장한다. 멱등키 uk 위반이면 롤백되어 C005가 된다(ADR 0027)
        orderRepository.save(order);

        // 9. 주문한 항목만 장바구니에서 지운다
        cart.removeItems(cartItemIds);

        // 10. 토스 결제창에 넘길 주문번호와 금액을 응답한다
        return OrderCreateResponse.from(order);
    }
}

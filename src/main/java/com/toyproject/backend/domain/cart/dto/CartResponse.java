package com.toyproject.backend.domain.cart.dto;

import com.toyproject.backend.domain.cart.entity.Cart;

import java.math.BigDecimal;
import java.util.List;

/**
 * [Phase 4 · 2단계] 장바구니 응답. 조회와 모든 쓰기가 이 모양을 돌려준다.
 *
 * <p>합계는 구매 불가 항목까지 포함한 현재가 기준이다 — 주문 금액은 주문의 {@code priceAtOrder}가 정한다.
 *
 * @param totalQuantity 항목 50종 × {@code int} 수량이 넘칠 수 있어 {@code long}이다
 */
public record CartResponse(

        List<CartItemResponse> items,
        long totalQuantity,
        BigDecimal totalPrice
) {

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();
        long totalQuantity = items.stream()
                .mapToLong(CartItemResponse::quantity)
                .sum();
        BigDecimal totalPrice = items.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, totalQuantity, totalPrice);
    }

    /** 장바구니 행이 없을 때. 조회가 행을 만들지 않는다. */
    public static CartResponse empty() {
        return new CartResponse(List.of(), 0L, BigDecimal.ZERO);
    }
}

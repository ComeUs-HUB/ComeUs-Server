package com.toyproject.backend.domain.cart.dto;

import com.toyproject.backend.domain.cart.entity.CartItem;
import com.toyproject.backend.domain.product.entity.Product;

import java.math.BigDecimal;

/**
 * [Phase 4 · 2단계] 장바구니 항목 응답.
 *
 * @param price       현재가. 스냅샷이 아니다
 * @param purchasable 재고 수량 대신 내리는 값이다(ADR 0016)
 */
public record CartItemResponse(

        Long cartItemId,
        Long productId,
        String name,
        BigDecimal price,
        int quantity,
        boolean purchasable
) {

    public static CartItemResponse from(CartItem item) {
        Product product = item.getProduct();
        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                item.isPurchasable());
    }
}

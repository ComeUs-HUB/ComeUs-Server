package com.toyproject.backend.domain.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * [Phase 4 · 3단계] {@code POST /api/cart/items} 요청.
 *
 * <p>{@link #MAX_QUANTITY}는 재고와 무관한 정책 상한이다 — 넘으면 C001, 재고 초과는 그 뒤 CT002가 본다.
 */
public record CartItemAddRequest(

        @NotNull(message = "상품은 필수입니다.")
        Long productId,

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 1 이상이어야 합니다.")
        @Max(value = CartItemAddRequest.MAX_QUANTITY, message = "수량은 한 번에 {value}개까지입니다.")
        Integer quantity
) {

    /** 한 요청에 담을 수 있는 수량 상한. */
    public static final int MAX_QUANTITY = 99;
}

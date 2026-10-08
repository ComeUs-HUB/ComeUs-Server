package com.toyproject.backend.domain.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * [Phase 4 · 3단계] {@code PUT /api/cart/items/{itemId}} 요청. 합산이 아니라 교체다.
 *
 * <p>{@code 0}은 삭제로 해석하지 않고 C001로 거절한다 — {@code DELETE}가 이미 있다.
 */
public record CartItemQuantityRequest(

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 1 이상이어야 합니다.")
        @Max(value = CartItemAddRequest.MAX_QUANTITY, message = "수량은 {value}개까지입니다.")
        Integer quantity
) {
}

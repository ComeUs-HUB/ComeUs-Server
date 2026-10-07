package com.toyproject.backend.domain.cart.controller;

import com.toyproject.backend.domain.cart.dto.CartItemAddRequest;
import com.toyproject.backend.domain.cart.dto.CartItemQuantityRequest;
import com.toyproject.backend.domain.cart.dto.CartResponse;
import com.toyproject.backend.domain.cart.service.CartService;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Phase 4 · 3단계] 장바구니 API. 모든 엔드포인트가 회원 전용이고 응답은 전체 장바구니다.
 *
 * <p>담기는 합산이면 수정이라 201이 아니라 200이다. 계약은 {@code docs/domains/purchase/cart.md}.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getMyCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(cartService.getMyCart(principal.getId()));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@AuthenticationPrincipal UserPrincipal principal,
                                             @Valid @RequestBody CartItemAddRequest request) {
        return ApiResponse.success(
                cartService.addItem(principal.getId(), request.productId(), request.quantity()));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<CartResponse> changeQuantity(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long itemId,
                                                    @Valid @RequestBody CartItemQuantityRequest request) {
        return ApiResponse.success(cartService.changeQuantity(principal.getId(), itemId, request.quantity()));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> removeItem(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long itemId) {
        return ApiResponse.success(cartService.removeItem(principal.getId(), itemId));
    }

    @DeleteMapping
    public ApiResponse<CartResponse> clear(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(cartService.clear(principal.getId()));
    }
}

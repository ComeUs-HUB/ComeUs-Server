package com.toyproject.backend.domain.order.controller;

import com.toyproject.backend.domain.order.dto.OrderCreateRequest;
import com.toyproject.backend.domain.order.dto.OrderCreateResponse;
import com.toyproject.backend.domain.order.dto.OrderDetailResponse;
import com.toyproject.backend.domain.order.dto.OrderSummaryResponse;
import com.toyproject.backend.domain.order.service.OrderService;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.exception.CommonErrorCode;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.response.PageResponse;
import com.toyproject.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Phase 4 · 7단계] 회원 주문 API. 계약은 {@code docs/domains/purchase/order.md}.
 *
 * <p>생성은 멱등 재요청이면 기존 주문을 돌려주므로 201이 아니라 200이다.
 */
@RestController
@RequestMapping("/api/orders")
@Validated
@RequiredArgsConstructor
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String SORTABLE_FIELD = "createdAt";

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final OrderService orderService;

    /** 헤더를 필수로 받으면 누락이 500이 되므로 {@code required = false}와 검증으로 C001을 낸다(ADR 0024). */
    @PostMapping
    public ApiResponse<OrderCreateResponse> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @NotBlank(message = "멱등키는 필수입니다.")
            @Pattern(regexp = UUID_PATTERN, message = "멱등키는 UUID 형식이어야 합니다.")
            String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.createOrder(
                principal.getId(), idempotencyKey, request.cartItemIds(), request.toShippingAddress()));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> getMyOrderList(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(orderService.getMyOrderList(principal.getId(), sanitize(pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getMyOrderDetail(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long id) {
        return ApiResponse.success(orderService.getMyOrderDetail(principal.getId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelOrder(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id) {
        orderService.cancelOrder(principal.getId(), id);
        return ApiResponse.success(null);
    }

    /** @throws BusinessException 허용되지 않은 정렬 필드면 C001(400) */
    private Pageable sanitize(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELD.equals(order.getProperty())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
            }
        }
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort().and(Sort.by("id")));
    }
}

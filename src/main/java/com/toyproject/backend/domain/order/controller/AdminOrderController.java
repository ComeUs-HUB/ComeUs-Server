package com.toyproject.backend.domain.order.controller;

import com.toyproject.backend.domain.order.dto.OrderStatusUpdateRequest;
import com.toyproject.backend.domain.order.dto.AdminOrderSummaryResponse;
import com.toyproject.backend.domain.order.service.OrderService;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.exception.CommonErrorCode;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** [Phase 4 · 7단계] ADMIN 주문 API. 권한 판정은 필터체인에서 끝난다. */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String SORTABLE_FIELD = "createdAt";

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<AdminOrderSummaryResponse>> getOrderList(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(orderService.getOrderList(sanitize(pageable)));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeOrderStatus(@PathVariable Long id,
                                               @Valid @RequestBody OrderStatusUpdateRequest request) {
        orderService.changeOrderStatus(id, request.status());
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

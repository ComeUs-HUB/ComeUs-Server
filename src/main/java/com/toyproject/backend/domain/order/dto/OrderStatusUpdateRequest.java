package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** [Phase 4 · 7단계] {@code PUT /api/admin/orders/{id}/status} 요청. */
public record OrderStatusUpdateRequest(

        @NotNull(message = "상태는 필수입니다.")
        OrderStatus status
) {
}

package com.toyproject.backend.domain.order.dto;

import com.toyproject.backend.domain.order.entity.ShippingAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** [Phase 4 · 7단계] {@code POST /api/orders} 요청. 길이는 {@code orders} 컬럼과 맞춘다. */
public record OrderCreateRequest(

        @NotEmpty(message = "주문할 항목은 필수입니다.")
        List<@NotNull(message = "항목 id는 필수입니다.") Long> cartItemIds,

        @NotBlank(message = "받는 사람은 필수입니다.")
        @Size(max = 50, message = "받는 사람은 {max}자까지입니다.")
        String recipientName,

        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 20, message = "연락처는 {max}자까지입니다.")
        String recipientPhone,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 {max}자까지입니다.")
        String address
) {

    public ShippingAddress toShippingAddress() {
        return ShippingAddress.of(recipientName, recipientPhone, address);
    }
}

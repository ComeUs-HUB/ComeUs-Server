package com.toyproject.backend.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [Phase 4 · 4단계] 주문 시점의 배송지 스냅샷. 회원 정보가 바뀌어도 성립한 주문의 배송지는 그대로다.
 *
 * <p>{@code User}의 같은 값은 nullable이지만 주문에는 필수다 — 비어 있으면 요청 DTO가 C001로 거른다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 255)
    private String address;

    private ShippingAddress(String recipientName, String recipientPhone, String address) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.address = address;
    }

    public static ShippingAddress of(String recipientName, String recipientPhone, String address) {
        return new ShippingAddress(recipientName, recipientPhone, address);
    }
}

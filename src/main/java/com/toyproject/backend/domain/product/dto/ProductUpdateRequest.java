package com.toyproject.backend.domain.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * [Phase 3 · 8단계] {@code PUT /api/products/{id}}의 요청 바디.
 *
 * <p><b>부분 수정이 아니라 전체 교체다</b>({@code CategoryUpdateRequest}와 같은 규약).
 *
 * <p>이 요청은 {@code HIDDEN}을 <b>푼다</b> — ADMIN이 soft delete를 되돌리는 유일한 통로다(ADR 0013).
 *
 * @param description 없어도 된다
 * @param imageUrls   전체 교체다. 생략하면 기존 이미지가 모두 사라진다
 */
public record ProductUpdateRequest(

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotBlank(message = "상품 이름은 필수입니다.")
        @Size(max = 100, message = "상품 이름은 100자를 넘을 수 없습니다.")
        String name,

        @Size(max = 16_000, message = "상품 설명은 16000자를 넘을 수 없습니다.")
        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Positive(message = "가격은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 2, message = "가격은 소수점 두 자리까지의 8자리 수입니다.")
        BigDecimal price,

        @NotNull(message = "재고는 필수입니다.")
        @PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
        Integer stockQuantity,

        List<@NotBlank @Size(max = 500, message = "이미지 URL은 500자를 넘을 수 없습니다.") String> imageUrls
) {

    /** {@code null}을 빈 목록으로 정규화한다 — 서비스가 null 검사를 하지 않아도 되게. */
    public ProductUpdateRequest {
        imageUrls = (imageUrls == null) ? List.of() : List.copyOf(imageUrls);
    }
}

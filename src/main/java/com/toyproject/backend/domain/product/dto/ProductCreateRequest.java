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
 * [Phase 3 · 8단계] {@code POST /api/products}의 요청 바디.
 *
 * <p>재고 0으로 등록하면 {@code SOLD_OUT}으로 시작한다 — 상태는 요청이 정하지 못한다.
 *
 * <p>{@code @Size}·{@code @Digits}는 {@code products} 컬럼과 맞춘 값이다. 어긋나면
 * C001이어야 할 실패가 DB까지 내려가 C005로 나간다.
 *
 * @param description 없어도 된다
 * @param imageUrls   생략하면 빈 목록이다. 담은 순서가 곧 전시 순서다
 */
public record ProductCreateRequest(

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotBlank(message = "상품 이름은 필수입니다.")
        @Size(max = 100, message = "상품 이름은 100자를 넘을 수 없습니다.")
        String name,

        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Positive(message = "가격은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 2, message = "가격은 소수점 두 자리까지의 8자리 수입니다.")
        BigDecimal price,

        @PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
        int stockQuantity,

        List<@NotBlank @Size(max = 500, message = "이미지 URL은 500자를 넘을 수 없습니다.") String> imageUrls
) {

    /** {@code null}을 빈 목록으로 정규화한다 — 서비스가 null 검사를 하지 않아도 되게. */
    public ProductCreateRequest {
        imageUrls = (imageUrls == null) ? List.of() : List.copyOf(imageUrls);
    }
}

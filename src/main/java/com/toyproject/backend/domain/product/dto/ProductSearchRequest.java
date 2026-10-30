package com.toyproject.backend.domain.product.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * [Phase 3 · 7단계] {@code GET /api/products}의 쿼리 파라미터.
 *
 * <p>{@code categoryId}가 단수인 것이 {@link ProductSearchCondition}과 갈리는 지점이다 —
 * 서비스가 하위 소분류까지 펼친 뒤 Condition을 만든다.
 *
 * <p>모든 필드가 optional이다. 가격 상·하한이 뒤집혀도 막지 않는다 — 빈 결과가 될 뿐 오류가 아니다.
 *
 * @param categoryId 대분류면 하위 소분류 상품까지 포함된다
 * @param keyword    빈 문자열·공백은 조건 없음으로 취급된다
 */
public record ProductSearchRequest(

        Long categoryId,

        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        BigDecimal minPrice,

        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        BigDecimal maxPrice,

        String keyword
) {
}

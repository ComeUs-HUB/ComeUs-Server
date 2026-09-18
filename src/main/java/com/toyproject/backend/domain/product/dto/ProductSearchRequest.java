package com.toyproject.backend.domain.product.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * [Phase 3 · 7단계] 상품 목록 검색 요청
 *
 * <p><b>역할</b><br>
 * {@code GET /api/products}의 쿼리 파라미터를 바인딩한다. <b>클라이언트가 보낸 형태 그대로</b>이며,
 * 서비스가 이것을 {@link ProductSearchCondition}으로 바꾼다.
 *
 * <p><b>⚠️ {@code categoryId}가 단수인 것이 {@link ProductSearchCondition}과 갈리는 지점이다</b><br>
 * 클라이언트는 카테고리 하나를 고른다. 대분류를 골랐다면 그 아래 소분류 상품까지 나와야 하므로
 * 서비스가 {@code CategoryService}를 경유해 <b>{@code [자기 자신 + 자식들]}로 펼친 뒤</b>
 * {@code ProductSearchCondition}을 만든다.
 *
 * <p>타입을 나눈 이유가 여기 있다 — 하나로 두면 같은 필드가 "펼치기 전"과 "펼친 후"를 모두
 * 뜻하게 되어, 받는 쪽에서 어느 쪽인지 알 수 없다.
 *
 * <p><b>모든 필드가 optional이다.</b> 아무것도 보내지 않으면 숨긴 상품을 뺀 전체가 나온다.
 * 그래서 {@code @NotNull}을 붙이지 않는다 — 여기서 막는 것은 <b>말이 안 되는 값</b>뿐이다.
 *
 * <p><b>가격 상·하한이 뒤집힌 조합은 막지 않는다.</b> {@code minPrice > maxPrice}면 결과가 빈
 * 목록이 될 뿐 오류가 아니다. 두 필드를 함께 보는 검증은 클래스 레벨 제약이 필요해 이득보다 비싸다.
 *
 * @param categoryId 카테고리 id. 대분류면 하위 소분류 상품까지 포함된다
 * @param minPrice   가격 하한(이상)
 * @param maxPrice   가격 상한(이하)
 * @param keyword    상품명 부분 일치. 빈 문자열·공백은 조건 없음으로 취급된다
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

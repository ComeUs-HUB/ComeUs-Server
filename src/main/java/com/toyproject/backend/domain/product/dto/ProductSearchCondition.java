package com.toyproject.backend.domain.product.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * [Phase 3 · 6단계] 서비스가 조립한 내부 검색 조건. HTTP 경계를 건너지 않는다.
 *
 * <p>{@link ProductSearchRequest}와 나눈 이유는 {@code categoryIds}에 있다 — 바깥에서 오는 것은
 * <b>단수</b> {@code categoryId}이고 여기 담기는 것은 <b>펼쳐진 목록</b>이라, 한 타입으로 두면
 * 펼치기 전후가 타입에서 드러나지 않는다.
 *
 * <p>모든 필드가 nullable이다 — 값이 없는 조건은 WHERE 절에서 빠진다.
 *
 * @param categoryIds 자기 자신 + 하위 카테고리. 펼치는 책임은 {@code ProductService}에 있다
 * @param keyword     {@code null}과 공백 문자열 모두 "조건 없음"이다
 */
public record ProductSearchCondition(

        List<Long> categoryIds,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String keyword
) {
}

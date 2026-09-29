package com.toyproject.backend.domain.product.entity;

/**
 * [Phase 3 · 5단계] 상품 상태.
 *
 * <p>재고 축({@code ON_SALE} ↔ {@code SOLD_OUT})과 삭제 축({@code HIDDEN})은 독립이다 —
 * 재고가 차도 {@code HIDDEN}은 풀리지 않는다({@link Product#increaseStock(int)}).
 *
 * <p>{@code HIDDEN}은 soft delete이며 행은 남는다(ADR 0013).
 * DB에는 네이티브 ENUM이 아니라 {@code VARCHAR(20)}으로 저장된다.
 */
public enum ProductStatus {

    ON_SALE,
    SOLD_OUT,
    HIDDEN
}

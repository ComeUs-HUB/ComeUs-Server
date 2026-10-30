package com.toyproject.backend.domain.category.dto;

import com.toyproject.backend.domain.category.entity.Category;

/**
 * [Phase 3 · 3단계] {@code POST}·{@code PUT /api/categories}의 응답 바디. 한 건만 담는다.
 *
 * <p>{@link CategoryTreeResponse}와 나눈 것은 생성·수정 결과에 중첩이 의미가 없기 때문이다.
 * 합치면 {@code POST} 응답에 항상 빈 {@code children}이 따라붙는다.
 * <p>부모는 객체가 아니라 {@code parentId}만 내린다.
 *
 * @param parentId 대분류면 {@code null}
 */
public record CategoryResponse(

        Long id,
        String name,
        int displayOrder,
        Long parentId
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDisplayOrder(),
                category.isRoot() ? null : category.getParent().getId());
    }
}

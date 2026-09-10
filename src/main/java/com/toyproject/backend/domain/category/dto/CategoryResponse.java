package com.toyproject.backend.domain.category.dto;

import com.toyproject.backend.domain.category.entity.Category;

/**
 * [Phase 3 · 3단계] 카테고리 단건 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code POST}·{@code PUT /api/categories}의 응답 바디. 방금 만들거나 고친 <b>그 한 건</b>만 담는다.
 *
 * <p><b>{@link CategoryTreeResponse}와 나눈 이유</b><br>
 * 트리 응답은 중첩({@code children})이 본질이지만, 생성·수정 결과에는 중첩이 의미가 없다.
 * 하나로 합치면 {@code POST} 응답에 항상 빈 {@code children}이 따라붙어 "이게 왜 여기 있지"가 된다.
 *
 * <p>부모는 객체가 아니라 <b>{@code parentId}만</b> 내린다. 부모 전체를 직렬화하면 응답이 커지고,
 * 클라이언트가 이 시점에 필요한 것은 "어디에 붙었는가"뿐이다.
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

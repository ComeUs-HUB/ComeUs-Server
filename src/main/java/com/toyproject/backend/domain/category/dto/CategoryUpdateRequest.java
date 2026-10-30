package com.toyproject.backend.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * [Phase 3 · 3단계] {@code PUT /api/categories/{id}}의 요청 바디. 부분 수정이 아니다.
 *
 * <p>{@code name}·{@code displayOrder}는 둘 다 필수라 비어 있는 상태가 존재하지 않는다.
 * {@code UserUpdateRequest}의 "null이면 변경하지 않음" 규약이 적용되지 않는다.
 * <p>부모를 바꾸는 필드를 두지 않는다 — 깊이 불변식이 엔티티와 Repository 양쪽으로 갈라진다.
 * 그 결과 깊이를 흔들 수 있는 경로는 {@code Category.child()} 하나로 남는다.
 */
public record CategoryUpdateRequest(

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(max = 50, message = "카테고리 이름은 50자를 넘을 수 없습니다.")
        String name,

        @PositiveOrZero(message = "전시 순서는 0 이상이어야 합니다.")
        int displayOrder
) {
}

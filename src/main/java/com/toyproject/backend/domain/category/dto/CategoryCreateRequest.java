package com.toyproject.backend.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * [Phase 3 · 3단계] {@code POST /api/categories}의 요청 바디.
 *
 * <p>{@code parentId}의 유무가 대분류·소분류를 가른다. 별도 플래그를 두지 않아
 * 플래그와 {@code parentId}가 어긋나는 조합이 아예 생기지 않는다.
 * <p>{@code @Size(max = 50)}은 {@code categories.name}과 맞춘다 — 어긋나면 C001이어야 할
 * 실패가 DB까지 내려가 C005로 나간다.
 *
 * @param displayOrder 0 이상. 같은 값이 있어도 막지 않는다(순서는 id로 확정된다)
 * @param parentId     상위 대분류 id. 대분류를 만들 때는 보내지 않는다
 */
public record CategoryCreateRequest(

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(max = 50, message = "카테고리 이름은 50자를 넘을 수 없습니다.")
        String name,

        @PositiveOrZero(message = "전시 순서는 0 이상이어야 합니다.")
        int displayOrder,

        Long parentId
) {
}

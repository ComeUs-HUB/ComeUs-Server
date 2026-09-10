package com.toyproject.backend.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * [Phase 3 · 3단계] 카테고리 생성 요청 DTO
 *
 * <p><b>역할</b><br>
 * {@code POST /api/categories}의 요청 바디.
 *
 * <p><b>{@code parentId}가 분기를 정한다</b>
 * <ul>
 *   <li>{@code null} → 대분류. {@code Category.root()}로 만든다</li>
 *   <li>값 있음 → 소분류. 부모를 조회해 {@code Category.child()}로 만든다.
 *       부모가 없으면 CA001, 부모가 이미 소분류면 <b>CA003</b>이다</li>
 * </ul>
 * "대분류를 만들려면 무엇을 보내야 하는가"를 별도 플래그로 두지 않고 {@code parentId}의 유무로
 * 표현한다 — 플래그와 {@code parentId}가 어긋나는 조합(예: {@code isRoot=true}인데 {@code parentId}도 있음)이
 * 아예 생기지 않는다.
 *
 * <p><b>{@code name}은 필수다.</b> {@code UserUpdateRequest}의 "null이면 변경하지 않음" 규약은
 * 여기 적용되지 않는다 — 생성 요청이라 비워 둘 수 있는 상태가 없다.
 *
 * <p>{@code @Size(max = 50)}은 {@code categories.name}과 맞춘 값이다. 어긋나면
 * <b>C001이어야 할 실패가 DB까지 내려가 C005({@code DataIntegrityViolationException})로 나간다.</b>
 *
 * @param displayOrder 전시 순서. 0 이상. 같은 값이 있어도 막지 않는다(순서는 id로 확정된다)
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

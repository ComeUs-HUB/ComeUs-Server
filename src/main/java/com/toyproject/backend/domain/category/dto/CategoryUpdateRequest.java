package com.toyproject.backend.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * [Phase 3 · 3단계] 카테고리 수정 요청 DTO
 *
 * <p><b>역할</b><br>
 * {@code PUT /api/categories/{id}}의 요청 바디.
 *
 * <p><b>⚠️ 부분 수정이 아니다 — {@code UserUpdateRequest}와 다르다.</b><br>
 * 회원 프로필은 모든 필드가 optional이고 {@code null}이 "변경하지 않음"을 뜻한다. 카카오 로그인만으로
 * 가입되어 프로필이 나중에 조금씩 채워지기 때문이다. 카테고리는 사정이 다르다 —
 * {@code name}과 {@code displayOrder}는 <b>둘 다 필수값</b>이라 비어 있는 상태가 애초에 존재하지 않는다.
 * 그래서 {@code @NotBlank}로 막고, 보내지 않으면 C001로 거절한다.
 *
 * <p><b>부모는 바꿀 수 없다.</b> 필드 자체를 두지 않았다. 부모를 옮기려면 검증이 양쪽으로 갈라지는데
 * ("새 부모가 대분류인가"는 엔티티가 알지만 "내가 자식을 갖고 있지 않은가"는 Repository를 봐야 한다),
 * 계약에 없는 기능을 위해 깊이 불변식을 두 곳으로 쪼갤 이유가 없다.
 * 그 결과 <b>깊이를 흔들 수 있는 경로는 {@code Category.child()} 하나로 남는다</b> —
 * 자세한 근거는 {@code Category} Javadoc 참고.
 */
public record CategoryUpdateRequest(

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(max = 50, message = "카테고리 이름은 50자를 넘을 수 없습니다.")
        String name,

        @PositiveOrZero(message = "전시 순서는 0 이상이어야 합니다.")
        int displayOrder
) {
}

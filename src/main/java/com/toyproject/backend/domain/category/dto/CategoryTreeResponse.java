package com.toyproject.backend.domain.category.dto;

import com.toyproject.backend.domain.category.entity.Category;

import java.util.List;

/**
 * [Phase 3 · 3단계] 카테고리 트리 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code GET /api/categories}의 응답 바디. 대분류 배열이고, 각 대분류가 소분류 배열을 품는다.
 *
 * <p><b>2depth 계약이 타입에 드러난다</b><br>
 * 소분류는 {@link #of(Category)}로 만들어 {@code children}이 <b>항상 빈 리스트</b>다.
 * 깊이가 무제한이었다면 이 record는 자기 자신을 재귀적으로 품는 구조가 되고, FE도 재귀 렌더링을
 * 해야 했다. 2depth 고정의 실익이 응답 형태에서 그대로 나타난다.
 *
 * <p>{@code children}을 {@code null}이 아니라 <b>빈 리스트</b>로 두는 이유: 클라이언트가
 * null 검사 없이 순회할 수 있고, JSON에서도 {@code []}로 일관되게 나간다.
 *
 * @param children 대분류면 소분류 목록, 소분류면 빈 리스트
 */
public record CategoryTreeResponse(

        Long id,
        String name,
        int displayOrder,
        List<CategoryTreeResponse> children
) {

    /** 소분류(리프) 변환. {@code children}은 비어 있다. */
    public static CategoryTreeResponse of(Category category) {
        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                category.getDisplayOrder(),
                List.of());
    }

    /** 대분류 변환. 서비스가 메모리에서 묶어 둔 소분류 목록을 받는다. */
    public static CategoryTreeResponse of(Category category, List<CategoryTreeResponse> children) {
        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                category.getDisplayOrder(),
                children);
    }
}

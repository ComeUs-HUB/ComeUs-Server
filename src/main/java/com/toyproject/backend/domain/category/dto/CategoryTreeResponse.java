package com.toyproject.backend.domain.category.dto;

import com.toyproject.backend.domain.category.entity.Category;

import java.util.List;

/**
 * [Phase 3 · 3단계] {@code GET /api/categories}의 응답 바디. 대분류가 소분류를 품는다.
 *
 * <p>2depth 계약이 타입에 드러난다 — 소분류의 {@code children}은 항상 비어 있고,
 * 깊이가 무제한이었다면 재귀 구조가 되어 FE도 재귀 렌더링을 해야 했다.
 * <p>{@code children}은 {@code null}이 아니라 빈 리스트다. 클라이언트가 null 검사 없이 순회한다.
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

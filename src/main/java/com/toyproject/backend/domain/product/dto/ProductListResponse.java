package com.toyproject.backend.domain.product.dto;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductImage;
import com.toyproject.backend.domain.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * [Phase 3 · 7단계] {@code GET /api/products}의 {@code content} 한 칸.
 *
 * <p>{@link ProductDetailResponse}와 나눈 이유는 크기다 — 목록은 한 번에 최대 100건이라
 * {@code description}과 이미지 전체를 담으면 바디가 그만큼 커진다.
 *
 * <p>{@code stockQuantity}와 {@code categoryName}을 담지 않는다. 전자는 {@code status}가
 * 이미 말하고(ADR 0015), 후자는 읽는 순간 LAZY 프록시가 깨져 카테고리마다 SELECT가 붙는다.
 * 필드 추가는 하위호환이지만 제거는 계약 파기라, 필요해질 때 늘린다.
 *
 * @param categoryId   {@code getCategory().getId()}는 프록시를 초기화하지 않아 공짜다
 * @param thumbnailUrl {@code displayOrder}가 가장 작은 한 장. 이미지가 없으면 {@code null}
 */
public record ProductListResponse(

        Long id,
        String name,
        BigDecimal price,
        ProductStatus status,
        Long categoryId,
        String thumbnailUrl
) {

    public static ProductListResponse from(Product product) {
        return new ProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStatus(),
                product.getCategory().getId(),
                thumbnailUrlOf(product));
    }

    /** 엔티티에 {@code @OrderBy}를 걸지 않았으므로 DB 순서를 믿지 않고 여기서 고른다. */
    private static String thumbnailUrlOf(Product product) {
        return product.getImages().stream()
                .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }
}

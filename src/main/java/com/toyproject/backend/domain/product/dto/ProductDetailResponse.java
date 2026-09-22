package com.toyproject.backend.domain.product.dto;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductImage;
import com.toyproject.backend.domain.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * [Phase 3 · 7단계] {@code GET /api/products/{id}}의 응답 바디.
 *
 * <p>이름은 상세 조회를 가리키지만 <b>8단계의 {@code POST}·{@code PUT} 응답도 같은 모양</b>이라
 * 그때 이 타입을 공유한다.
 *
 * <p>{@code categoryName}을 담지 않는다 — 이름을 읽으면 LAZY 프록시가 깨지고, 브레드크럼을
 * 그리려면 부모 이름까지 필요해 프록시를 두 번 깨야 한다. 카테고리 트리는 공개 API가
 * 쿼리 1회로 돌려준다.
 *
 * @param status    품절 여부를 이 필드가 표현한다. <b>재고 수량은 담지 않는다</b>(ADR 0015)
 * @param imageUrls {@code displayOrder} 오름차순. 없으면 빈 배열이다 —
 *                  클라이언트가 null 검사 없이 순회할 수 있어야 한다
 */
public record ProductDetailResponse(

        Long id,
        String name,
        String description,
        BigDecimal price,
        ProductStatus status,
        Long categoryId,
        List<String> imageUrls
) {

    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus(),
                product.getCategory().getId(),
                imageUrlsOf(product));
    }

    /** 엔티티에 {@code @OrderBy}를 걸지 않았으므로 DB 순서를 믿지 않고 여기서 정렬한다. */
    private static List<String> imageUrlsOf(Product product) {
        return product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(ProductImage::getImageUrl)
                .toList();
    }
}

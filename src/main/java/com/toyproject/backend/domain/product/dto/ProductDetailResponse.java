package com.toyproject.backend.domain.product.dto;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductImage;
import com.toyproject.backend.domain.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * [Phase 3 · 7단계] 상품 상세 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code GET /api/products/{id}}의 응답 바디. 한 상품의 <b>전부</b>를 담는다.
 *
 * <p>목록({@link ProductListResponse})과 달리 {@code description}과 이미지 전체를 싣는다.
 * 1건이라 바디 크기가 문제 되지 않고, 상세 화면이 실제로 그 값들을 쓴다.
 *
 * <p><b>쓰기 응답으로도 이 타입을 쓴다.</b> 8단계의 {@code POST}·{@code PUT}이 돌려줄 것과
 * 상세 조회가 돌려줄 것이 같은 모양이라 따로 만들 이유가 없다. 카테고리가
 * {@code CategoryResponse}와 {@code CategoryTreeResponse}를 나눈 것은 트리 응답에만
 * {@code children}이라는 구조가 있었기 때문이고, 상품에는 그런 차이가 없다.
 *
 * <p><b>{@code categoryName}을 담지 않는다.</b> 이름을 읽으면 LAZY 프록시가 깨져 SELECT가
 * 하나 더 나가는데, 카테고리 트리는 {@code GET /api/categories}가 쿼리 1회로 공개하고 있고
 * FE는 네비게이션 때문에 이미 갖고 있다. 게다가 브레드크럼("상의 &gt; 티셔츠")을 그리려면
 * 부모 이름까지 필요해 <b>프록시를 두 번 깨야 한다</b> — 필드 하나로 끝나는 문제가 아니다.
 * 딥링크 진입이 실제 문제가 되면 그때 카테고리 경로 객체로 추가한다.
 *
 * <p><b>이 응답은 숨긴 상품을 담지 않는다.</b> 서비스가 {@code findByIdAndStatusNot}으로
 * 걸러 P001(404)로 만들기 때문이다. 다만 8단계에서 {@code PUT} 응답으로 쓰일 때는
 * {@code HIDDEN}이었다가 되살아난 상품이 실릴 수 있다.
 *
 * @param imageUrls {@code displayOrder} 오름차순. 이미지가 없으면 빈 배열이다 —
 *                  {@code null}이 아니라 빈 배열이어야 클라이언트가 검사 없이 순회한다
 */
public record ProductDetailResponse(

        Long id,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
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
                product.getStockQuantity(),
                product.getStatus(),
                product.getCategory().getId(),
                imageUrlsOf(product));
    }

    /**
     * 이미지 URL을 전시 순서대로 편다.
     *
     * <p>엔티티 컬렉션에 {@code @OrderBy}를 걸지 않았으므로 <b>DB가 돌려주는 순서를 믿지 않고</b>
     * 여기서 정렬한다 — 순서가 필요한 곳은 응답을 만드는 지점뿐이라, 엔티티에 걸어 모든
     * 조회 경로가 {@code ORDER BY}를 지불하게 만들지 않는다.
     */
    private static List<String> imageUrlsOf(Product product) {
        return product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(ProductImage::getImageUrl)
                .toList();
    }
}

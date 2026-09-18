package com.toyproject.backend.domain.product.dto;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductImage;
import com.toyproject.backend.domain.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * [Phase 3 · 7단계] 상품 목록 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code GET /api/products}의 {@code content} 한 칸. <b>카드 하나를 그리는 데 필요한 만큼</b>만 담는다.
 *
 * <p><b>{@link ProductDetailResponse}와 나눈 이유</b><br>
 * 목록은 한 번에 최대 100건이 실린다. 여기에 {@code description}과 이미지 전체를 넣으면
 * 쿼리는 늘지 않지만 <b>응답 바디가 그만큼 커진다</b> — 이미지 5장짜리 상품 20건이면 URL 100개다.
 * 상세는 1건이라 사정이 다르다. {@code CategoryResponse}/{@code CategoryTreeResponse}를
 * 나눈 것과 같은 판단이다.
 *
 * <p><b>담지 않은 것과 그 이유</b>
 * <ul>
 *   <li>{@code stockQuantity} — 목록에서 필요한 것은 "살 수 있는가"이고 그것은 {@code status}가
 *       이미 말한다. 재고 수량은 상세에서 본다.</li>
 *   <li>{@code categoryName} — {@code GET /api/categories}가 전체 트리를 쿼리 1회로 돌려주고
 *       FE는 네비게이션 때문에 어차피 그것을 갖고 있다. 게다가 이름을 읽으면
 *       <b>LAZY 프록시가 깨져 카테고리마다 SELECT가 하나씩 붙는다</b>({@code Category}에는
 *       {@code @BatchSize}가 없다).</li>
 * </ul>
 * 둘 다 <b>필요해지면 추가할 수 있다</b> — 필드 추가는 하위호환이지만 제거는 계약 파기다.
 *
 * <p><b>{@code categoryId}는 공짜다.</b> {@code getCategory().getId()}는 프록시를 초기화하지 않는다 —
 * 식별자는 프록시가 이미 들고 있고, {@code category_id}는 {@code products} 행에 있기 때문이다
 * ({@code Category} Javadoc의 같은 설명 참고).
 *
 * @param thumbnailUrl 대표 이미지. {@code displayOrder}가 가장 작은 한 장이며,
 *                     이미지가 없으면 {@code null}이다. 기본 이미지를 무엇으로 할지는
 *                     화면의 결정이라 서버가 정하지 않는다
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

    /**
     * 대표 이미지 한 장을 고른다.
     *
     * <p>컬렉션에 {@code @OrderBy}를 걸지 않았으므로 <b>DB가 돌려주는 순서를 믿지 않고</b>
     * 여기서 {@code displayOrder} 최소값을 직접 고른다. 엔티티에 정렬을 걸면 이 상품을 읽는
     * 모든 경로가 {@code ORDER BY}를 지불하게 되는데, 순서가 필요한 곳은 응답을 만드는 여기뿐이다.
     */
    private static String thumbnailUrlOf(Product product) {
        return product.getImages().stream()
                .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }
}

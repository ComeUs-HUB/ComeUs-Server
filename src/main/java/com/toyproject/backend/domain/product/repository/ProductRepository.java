package com.toyproject.backend.domain.product.repository;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * [Phase 3 · 6단계] 상품 Repository
 *
 * <p><b>역할</b><br>
 * {@code Product}의 영속성 경계. 동적 필터 실행은 {@link JpaSpecificationExecutor}가 제공하는
 * {@code findAll(Specification, Pageable)}이 맡고, 여기에는 <b>이름만으로 해결되는 질의 둘</b>만 둔다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@code @Query}를 두지 않는다. 두 메서드 모두 쿼리 메서드 이름으로 해결된다 —
 *       {@code CategoryRepository}와 같은 판단이다.</li>
 *   <li>동적 조건은 <b>여기에 메서드를 늘려 해결하지 않는다.</b> 조건이 n개면 조합이 2ⁿ으로
 *       늘어나기 때문이며, 그래서 {@code ProductSpecifications}가 따로 있다
 *       ([ADR 0011](docs/decisions/0011-jpa-specification-for-product-filter.md)).</li>
 * </ul>
 *
 * <p><b>⚠️ 이 Repository는 category 패키지에서도 주입한다</b><br>
 * {@code CategoryRepository}에 걸린 "자기 패키지 안에서만 주입한다"는 규칙이 여기에는 없다.
 * {@link #existsByCategoryIdIn(List)}이 <b>{@code CategoryService}가 직접 쓰는 진입점</b>이며,
 * 이는 상품·카테고리 양방향 참조에서 순환을 끊기 위한 의도된 예외다
 * ([ADR 0014](docs/decisions/0014-category-reads-product-repository.md)).
 * 그 외의 도메인은 {@code ProductService}를 경유한다.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 주어진 카테고리들에 상품이 하나라도 붙어 있는지. <b>카테고리 삭제 가능 여부 판정용</b>이다.
     *
     * <p>{@code true}면 삭제할 수 없다(CA004). {@code CategoryService.delete()}가
     * 하위 카테고리 검사(CA002) <b>다음에</b> 호출한다.
     *
     * <p>목록을 받는 이유: 대분류를 지우려면 <b>그 아래 소분류에 붙은 상품까지</b> 봐야 한다.
     * 자기 자신만 검사하면 소분류에 상품이 있는 대분류가 지워진다.
     *
     * @param categoryIds 검사할 카테고리 id 목록(자기 자신 + 자식들)
     */
    boolean existsByCategoryIdIn(List<Long> categoryIds);

    /**
     * 특정 상태를 <b>제외하고</b> id로 조회한다. <b>공개 상세 조회 전용</b>이다.
     *
     * <p>{@code findByIdAndStatusNot(id, HIDDEN)}으로 호출해 soft delete된 상품을 거른다.
     * 없으면 빈 {@code Optional}이고, 서비스가 P001(404)로 바꾼다.
     *
     * <p>⚠️ <b>수정·삭제는 이 메서드를 쓰지 않는다.</b> 그쪽은 {@code findById}로 {@code HIDDEN}까지
     * 읽어야 ADMIN이 지운 상품을 되살릴 수 있다. 두 경로를 같은 헬퍼로 합치면 이 구분이 사라지므로
     * 합치지 않는다 ([ADR 0013](docs/decisions/0013-hide-soft-deleted-products-everywhere.md)).
     */
    Optional<Product> findByIdAndStatusNot(Long id, ProductStatus status);
}

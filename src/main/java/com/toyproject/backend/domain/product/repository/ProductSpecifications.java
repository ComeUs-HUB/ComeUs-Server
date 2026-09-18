package com.toyproject.backend.domain.product.repository;

import com.toyproject.backend.domain.product.dto.ProductSearchCondition;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * [Phase 3 · 6단계] 상품 목록 필터 조립
 *
 * <p><b>역할</b><br>
 * {@link ProductSearchCondition}의 값을 WHERE 절로 <b>번역</b>한다.
 * 조건이 있으면 조각을 만들고, 없으면 만들지 않는다.
 *
 * <p><b>⚠️ 공개 메서드는 {@link #search(ProductSearchCondition)} 하나뿐이다 — 이것이 설계다</b><br>
 * ADR 0011이 "Specification 조립을 서비스에 흩뿌리지 않고 한곳에 모은다 — 이것이 전환 비용을
 * 정하는 가장 큰 변수"라고 적어 둔 지점이 여기다. 조각을 {@code public}으로 열면 서비스가 골라
 * 쓰기 시작하고, 그 순간 QueryDSL로 갈아탈 때 고쳐야 할 범위가 <b>이 파일 하나에서 서비스
 * 전체로 번진다.</b> 서비스는 "조건 객체를 넘기면 페이지가 나온다"만 알면 된다.
 *
 * <p><b>조각이 {@code null}을 반환하면 "조건 없음"이다</b><br>
 * Spring Data가 {@code null} 조각을 WHERE 절에서 흘려보내므로, 값이 없을 때 {@code null}을
 * 돌려주기만 하면 조건이 빠진다. <b>호출부에 {@code if}가 필요 없고</b>, 그래서
 * {@link #search(ProductSearchCondition)}가 분기 없이 평평하게 읽힌다.
 *
 * <p><b>{@link #notHidden()}만 인자를 받지 않는다</b><br>
 * 나머지 조각은 값이 없으면 빠지지만 이것은 <b>항상 붙는다.</b> 뺄 인자가 없어야 호출자가 뺄 수
 * 없고, 그래야 나중에 필터가 추가돼도 HIDDEN 정책이 새지 않는다(ADR 0013).
 *
 * <p><b>하위 카테고리를 여기서 펼치지 않는다</b><br>
 * {@link #categoryIdIn(List)}은 <b>이미 펼쳐진 id 목록</b>을 받아 {@code IN}으로 번역할 뿐이다.
 * 대분류를 자식까지 펼치는 것은 {@code ProductService}가 {@code CategoryService}를 경유해 한다.
 * 이 클래스가 {@code static} 유틸이라 Repository를 주입받을 수 없고, 무엇보다
 * <b>없는 카테고리를 CA001(404)로 구분하려면 펼치기가 서비스에 있어야 하기 때문이다</b> —
 * 여기서 펼치면 그냥 빈 결과(200 + 0건)가 된다.
 *
 * <p><b>이 클래스의 대가</b><br>
 * Specification은 <b>런타임에 조립</b>되므로 아래 필드명 문자열의 오타가 컴파일에서 걸리지 않는다.
 * 상수로 모아 두어 오타 지점을 한곳에 가뒀지만 정적 검증이 되는 것은 아니다.
 * ADR 0011이 "필터 조합 테스트로 덮는다"고 적어 둔 이유이며,
 * {@code ProductRepositoryTest}가 실제 SQL로 조합을 훑는다.
 */
public final class ProductSpecifications {

    // Criteria API는 필드를 문자열로 지목한다. 오타가 컴파일에서 걸리지 않으므로 한곳에 모은다.
    private static final String STATUS = "status";
    private static final String CATEGORY = "category";
    private static final String ID = "id";
    private static final String PRICE = "price";
    private static final String NAME = "name";

    private ProductSpecifications() {
    }

    /**
     * 검색 조건 전체를 하나의 {@code Specification}으로 조립한다. <b>유일한 공개 진입점.</b>
     *
     * <p>조립은 {@code notHidden()} AND 나머지 전부다. 값이 없는 조각은 {@code null}을 돌려주고
     * WHERE 절에서 빠지므로, 조건을 하나도 보내지 않으면
     * <b>{@code status <> 'HIDDEN'} 하나만 남는다.</b>
     *
     * @param condition 필터 값 묶음. 필드가 전부 {@code null}이어도 된다
     * @return 페이징·정렬과 함께 {@code ProductRepository.findAll(spec, pageable)}에 넘길 조건
     */
    public static Specification<Product> search(ProductSearchCondition condition) {
        return Specification.allOf(
                notHidden(),
                categoryIdIn(condition.categoryIds()),
                priceGoe(condition.minPrice()),
                priceLoe(condition.maxPrice()),
                nameContains(condition.keyword()));
    }

    /**
     * soft delete된 상품을 제외한다. <b>항상 AND로 붙으며 뺄 수 없다.</b>
     *
     * <p>인자가 없는 것이 핵심이다 — 호출자에게 "이번엔 빼자"고 할 수단을 주지 않는다.
     */
    private static Specification<Product> notHidden() {
        return (root, query, cb) -> cb.notEqual(root.get(STATUS), ProductStatus.HIDDEN);
    }

    /**
     * 카테고리 목록에 속한 상품만 남긴다.
     *
     * <p>{@code root.get(CATEGORY).get(ID)}는 <b>조인을 만들지 않는다</b> — {@code products}의
     * FK 컬럼을 그대로 읽는다. {@code root.join(CATEGORY)}를 쓰면 불필요한 JOIN이 붙고
     * <b>{@code Page}가 함께 발행하는 count 쿼리까지 무거워진다.</b>
     *
     * @param categoryIds 이미 펼쳐진 id 목록. {@code null}이거나 비어 있으면 조건 없음
     */
    private static Specification<Product> categoryIdIn(List<Long> categoryIds) {
        return (root, query, cb) -> (categoryIds == null || categoryIds.isEmpty())
                ? null
                : root.get(CATEGORY).get(ID).in(categoryIds);
    }

    /** 가격 하한(이상). {@code minPrice}가 {@code null}이면 조건 없음. */
    private static Specification<Product> priceGoe(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null
                ? null
                : cb.greaterThanOrEqualTo(root.get(PRICE), minPrice);
    }

    /** 가격 상한(이하). {@code maxPrice}가 {@code null}이면 조건 없음. */
    private static Specification<Product> priceLoe(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null
                ? null
                : cb.lessThanOrEqualTo(root.get(PRICE), maxPrice);
    }

    /**
     * 상품명 부분 일치. 대소문자를 가리지 않는다.
     *
     * <p><b>{@code null}과 공백 문자열을 모두 거른다.</b> {@code ?keyword=}로 빈 값이 오면
     * {@code LIKE '%%'}가 되어 조건이 있으나 마나 한 채로 인덱스만 못 타게 되기 때문이다.
     *
     * <p>⚠️ 알려진 한계: 키워드에 {@code %}나 {@code _}가 들어오면 <b>와일드카드로 해석된다.</b>
     * 상품명 검색이라 피해가 크지 않아 지금은 이스케이프하지 않는다. 필요해지면
     * {@code cb.like(..., '\\')} 형태로 이스케이프 문자를 지정한다.
     *
     * @param keyword {@code null}이거나 공백뿐이면 조건 없음
     */
    private static Specification<Product> nameContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get(NAME)), pattern);
        };
    }
}

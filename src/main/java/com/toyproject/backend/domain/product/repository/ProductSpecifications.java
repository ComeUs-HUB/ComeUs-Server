package com.toyproject.backend.domain.product.repository;

import com.toyproject.backend.domain.product.dto.ProductSearchCondition;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * [Phase 3 · 6단계] 상품 목록 필터 조립.
 *
 * <p>공개 메서드가 {@link #search(ProductSearchCondition)} 하나뿐인 것이 설계다 — 조각을
 * 열면 QueryDSL 전환 범위가 이 파일에서 서비스 전체로 번진다(ADR 0011).
 * <p>조각은 값이 없으면 {@code null}을 돌려주고 Spring Data가 흘려보내, 분기가 없다.
 * <p>필드명이 문자열이라 오타가 컴파일에서 걸리지 않는다 — 정적 검증이 아니며
 * {@code ProductRepositoryTest}가 실제 SQL로 덮는다.
 */
public final class ProductSpecifications {

    private static final String STATUS = "status";
    private static final String CATEGORY = "category";
    private static final String ID = "id";
    private static final String PRICE = "price";
    private static final String NAME = "name";

    private ProductSpecifications() {
    }

    /**
     * 검색 조건 전체를 하나로 조립한다. 유일한 공개 진입점.
     *
     * <p>조건을 하나도 주지 않으면 {@code status <> 'HIDDEN'} 하나만 남는다.
     */
    public static Specification<Product> search(ProductSearchCondition condition) {
        return Specification.allOf(
                notHidden(),
                categoryIdIn(condition.categoryIds()),
                priceGoe(condition.minPrice()),
                priceLoe(condition.maxPrice()),
                nameContains(condition.keyword()));
    }

    /** 항상 AND로 붙는다. 인자가 없어야 호출자가 뺄 수 없다(ADR 0013). */
    private static Specification<Product> notHidden() {
        return (root, query, cb) -> cb.notEqual(root.get(STATUS), ProductStatus.HIDDEN);
    }

    /**
     * 이미 펼쳐진 카테고리 id 목록으로 거른다. 여기서 하위를 펼치지 않는다 —
     * 펼치기는 {@code ProductService}가 {@code CategoryService}를 경유해 한다.
     *
     * <p>{@code root.get().get()}은 FK 컬럼을 그대로 읽어 조인을 만들지 않는다.
     * {@code root.join()}을 쓰면 count 쿼리까지 무거워진다.
     */
    private static Specification<Product> categoryIdIn(List<Long> categoryIds) {
        return (root, query, cb) -> (categoryIds == null || categoryIds.isEmpty())
                ? null
                : root.get(CATEGORY).get(ID).in(categoryIds);
    }

    private static Specification<Product> priceGoe(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null
                ? null
                : cb.greaterThanOrEqualTo(root.get(PRICE), minPrice);
    }

    private static Specification<Product> priceLoe(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null
                ? null
                : cb.lessThanOrEqualTo(root.get(PRICE), maxPrice);
    }

    /**
     * 상품명 부분 일치. 대소문자를 가리지 않는다.
     *
     * <p>공백 문자열까지 거른다 — {@code ?keyword=}가 {@code LIKE '%%'}가 되면 인덱스만 못 탄다.
     * 키워드의 {@code %}·{@code _}는 와일드카드로 해석되며, 필요해지면 이스케이프를 추가한다.
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

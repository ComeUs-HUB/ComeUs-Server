package com.toyproject.backend.domain.product.repository;

import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * [Phase 3 · 6단계] 상품 Repository.
 *
 * <p>동적 필터는 {@link JpaSpecificationExecutor}가 맡고, 여기에는 이름으로 해결되는 질의 둘만 둔다.
 * 조건 조합마다 메서드를 늘리지 않는다 — 조합이 2ⁿ으로 늘어나기 때문이며 그래서
 * {@link ProductSpecifications}가 따로 있다(ADR 0011).
 *
 * <p>⚠️ 상속받은 {@code delete}·{@code deleteById}를 쓰지 않는다. 상품은 soft delete라
 * 행을 지우면 주문 이력이 깨진다 — 삭제는 {@code Product.hide()}뿐이다.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 그 카테고리에 상품이 하나라도 있는지. 카테고리 삭제 차단(CA004)에 쓴다.
     *
     * <p>{@code CategoryService}가 직접 주입해 쓰는 유일한 진입점이다(ADR 0014 — 거기서는
     * {@code existsByCategoryIdIn}으로 적혀 있다. 하위가 있으면 CA002가 먼저 막아 잎만 도달하므로
     * 목록을 받을 이유가 없어 단건으로 좁혔다).
     */
    boolean existsByCategoryId(Long categoryId);

    /**
     * 특정 상태를 제외하고 id로 조회한다. <b>공개 상세 조회 전용</b>이다.
     *
     * <p>수정·삭제는 {@code findById}로 {@code HIDDEN}까지 읽어야 ADMIN이 되살릴 수 있으므로
     * 두 경로를 합치지 않는다(ADR 0013).
     */
    Optional<Product> findByIdAndStatusNot(Long id, ProductStatus status);
}

package com.toyproject.backend.domain.category.repository;

import com.toyproject.backend.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * [Phase 3 · 3단계] {@code Category}의 영속성 경계. 트리 조회, 삭제 가능 판정,
 * 상품 필터가 쓰는 하위 id 조회를 제공한다.
 *
 * <p>이름으로 표현되지 않는 질의에만 {@code @Query}를 쓴다 — id만 뽑거나 self-join 조건이 섞일 때다.
 * <p>category 패키지 안에서만 주입한다. product는 {@code CategoryService}를 경유한다(ADR 0014).
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 전체 카테고리를 전시 순서대로 읽는다. 트리 조회의 유일한 쿼리다.
     *
     * <p>대·소분류를 가리지 않고 한 번에 가져온다 — 2depth가 고정이라 서비스가 메모리에서
     * 부모 id로 묶으면 끝이고, 전역 정렬 한 번으로 각 그룹의 상대 순서가 유지된다.
     * <p>{@code id}를 tiebreaker로 넣는다. {@code displayOrder}만으로는 값이 같은 행의
     * 순서를 DB가 임의로 정해 트리 응답이 요청마다 달라진다.
     */
    List<Category> findAllByOrderByDisplayOrderAscIdAsc();

    /**
     * 하위 카테고리 존재 여부. {@code true}면 삭제할 수 없다(CA002).
     *
     * <p>2depth라 손자를 따질 필요가 없어 이 한 번의 검사로 끝난다.
     */
    boolean existsByParentId(Long parentId);

    /**
     * 자기 자신과 직속 하위의 id. 상품 목록의 카테고리 필터가 쓴다.
     *
     * <p>2depth 고정이라 재귀가 없다. 엔티티가 아니라 id만 뽑으므로 {@code @Query}가 필요하다.
     * <p>빈 결과는 "그 카테고리가 없다"는 뜻이다 — 존재하면 자기 자신이 반드시 걸리므로
     * 호출부가 별도 조회 없이 CA001까지 판정한다.
     */
    @Query("select c.id from Category c where c.id = :categoryId or c.parent.id = :categoryId")
    List<Long> findSelfAndChildIds(@Param("categoryId") Long categoryId);
}

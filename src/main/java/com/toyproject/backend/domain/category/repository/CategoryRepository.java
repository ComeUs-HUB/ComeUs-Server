package com.toyproject.backend.domain.category.repository;

import com.toyproject.backend.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * [Phase 3 · 3단계] 카테고리 Repository
 *
 * <p><b>역할</b><br>
 * {@code Category}의 영속성 경계. 트리 조립에 쓸 전체 조회, 삭제 가능 여부 판정,
 * 그리고 상품 필터가 쓰는 하위 id 조회를 제공한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>이름만으로 해결되는 질의는 {@code @Query}를 두지 않는다. <b>엔티티가 아니라 id만 뽑거나
 *       self-join 조건이 섞이면</b> 이름으로 표현되지 않으므로 그때만 {@code @Query}를 쓴다
 *       ({@link #findSelfAndChildIds(Long)}).</li>
 *   <li><b>이 Repository는 category 패키지 안에서만 주입한다.</b> 다른 도메인(product)이 카테고리에
 *       접근할 때는 {@code CategoryService}를 경유해야 2depth 규칙과 조회 방식이 한 곳에 남는다 —
 *       {@code UserRepository}에 걸어 둔 규칙과 같다.</li>
 * </ul>
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 전체 카테고리를 전시 순서대로 읽는다. <b>트리 조회의 유일한 쿼리</b>다.
     *
     * <p>대분류와 소분류를 <b>가리지 않고 한 번에</b> 가져온다. 2depth가 고정이라 서비스가 메모리에서
     * 부모 id로 묶으면 끝이고, 재귀 CTE도 깊이만큼의 반복 조회도 필요 없다.
     *
     * <p>전역 정렬 한 번으로 <b>각 그룹의 상대 순서가 그대로 유지된다</b> — 대분류끼리도,
     * 한 대분류 아래 소분류끼리도 {@code displayOrder} 순이 된다.
     *
     * <p>⚠️ <b>{@code id}를 tiebreaker로 넣은 이유</b>: {@code displayOrder}만으로 정렬하면 값이 같은
     * 행들의 순서를 DB가 임의로 정한다. 그러면 같은 데이터에 대해 트리 응답이 요청마다 달라질 수 있다.
     */
    List<Category> findAllByOrderByDisplayOrderAscIdAsc();

    /**
     * 하위 카테고리 존재 여부. <b>삭제 가능 여부 판정용</b>이다.
     *
     * <p>{@code true}면 삭제할 수 없다(CA002). 2depth라 손자를 따질 필요가 없어 이 한 번의 검사로 끝난다.
     *
     * @param parentId 상위 카테고리 id
     */
    boolean existsByParentId(Long parentId);

    /**
     * 자기 자신과 <b>직속 하위</b> 카테고리의 id를 한 번에 읽는다.
     * <b>상품 목록의 카테고리 필터가 쓰는 질의</b>다.
     *
     * <p>대분류를 고르면 그 아래 소분류 상품까지 나와야 하므로 {@code category_id IN (...)}에 넣을
     * id 목록이 필요하다. 2depth가 고정이라 <b>재귀가 없다</b> — 소분류를 넘기면 자식이 없어
     * 자기 자신 하나만 돌아온다.
     *
     * <p><b>⚠️ 빈 결과는 "그 카테고리가 없다"는 뜻이다.</b> 존재하는 카테고리는 {@code c.id = :categoryId}에
     * 반드시 걸리므로 결과가 최소 1개다. 덕분에 존재 확인을 위한 별도 조회 없이 이 쿼리 하나로
     * CA001 판정까지 끝난다 — 호출자({@code CategoryService.collectSelfAndChildIds})가 그렇게 쓴다.
     *
     * <p>엔티티가 아니라 <b>id만</b> 뽑는다. 필터에 필요한 것이 id뿐이라 나머지 컬럼을 읽을 이유가 없고,
     * 이 형태는 쿼리 메서드 이름으로 표현되지 않아 {@code @Query}가 필요하다.
     */
    @Query("select c.id from Category c where c.id = :categoryId or c.parent.id = :categoryId")
    List<Long> findSelfAndChildIds(@Param("categoryId") Long categoryId);
}

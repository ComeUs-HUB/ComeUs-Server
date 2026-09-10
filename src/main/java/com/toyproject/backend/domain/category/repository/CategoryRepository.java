package com.toyproject.backend.domain.category.repository;

import com.toyproject.backend.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [Phase 3 · 3단계] 카테고리 Repository
 *
 * <p><b>역할</b><br>
 * {@code Category}의 영속성 경계. 트리 조립에 쓸 전체 조회와, 삭제 가능 여부 판정을 제공한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>두 메서드 모두 쿼리 메서드 이름만으로 해결되므로 {@code @Query}를 두지 않는다.</li>
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
}

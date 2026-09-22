package com.toyproject.backend.domain.category.repository;

import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Phase 3 · 3단계] {@link CategoryRepository} 검증
 *
 * <p><b>여기서만 확인되는 것은 정렬이다.</b> {@code displayOrder}·{@code id} 두 키의 조합은
 * 쿼리 메서드 이름이 만들어내는 SQL의 {@code ORDER BY}에 달려 있어, 모킹으로는 검증되지 않는다.
 *
 * <p><b>애노테이션 근거</b>는 {@code UserRepositoryTest}와 같다 —
 * {@code @Import(JpaConfig.class)}가 없으면 auditing이 꺼져 {@code createdAt}(NOT NULL)이 채워지지 않고,
 * {@code @AutoConfigureTestDatabase(replace = NONE)}가 없으면 {@code MODE=MySQL} 설정이 무시된다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CategoryRepository")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("findAll… — 대분류와 소분류를 가리지 않고 전시 순서대로 한 번에 읽는다")
    void findAllOrdered() {
        Category outer = categoryRepository.save(Category.root("아우터", 2));
        Category top = categoryRepository.save(Category.root("상의", 1));
        categoryRepository.save(Category.child(top, "티셔츠", 1));
        categoryRepository.save(Category.child(outer, "코트", 1));

        List<Category> all = categoryRepository.findAllByOrderByDisplayOrderAscIdAsc();

        assertThat(all)
                .as("트리 조회는 이 쿼리 하나로 끝난다 — 깊이별 반복 조회가 없다")
                .hasSize(4);
        assertThat(all).extracting(Category::getName)
                .containsExactly("상의", "티셔츠", "코트", "아우터");
    }

    @Test
    @DisplayName("findAll… — displayOrder가 같으면 id 순으로 확정된다 (응답이 흔들리지 않는다)")
    void findAllTieBrokenById() {
        Category first = categoryRepository.save(Category.root("상의", 1));
        Category second = categoryRepository.save(Category.root("하의", 1));
        Category third = categoryRepository.save(Category.root("아우터", 1));

        List<Category> all = categoryRepository.findAllByOrderByDisplayOrderAscIdAsc();

        assertThat(all).extracting(Category::getId)
                .as("tiebreaker가 없으면 이 순서를 DB가 임의로 정한다")
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    @DisplayName("existsByParentId — 하위가 있으면 true (삭제를 막는 근거)")
    void existsByParentId_hasChild() {
        Category top = categoryRepository.save(Category.root("상의", 1));
        categoryRepository.save(Category.child(top, "티셔츠", 1));

        assertThat(categoryRepository.existsByParentId(top.getId())).isTrue();
    }

    @Test
    @DisplayName("existsByParentId — 하위가 없으면 false (대분류·소분류 모두)")
    void existsByParentId_noChild() {
        Category top = categoryRepository.save(Category.root("상의", 1));
        Category child = categoryRepository.save(Category.child(top, "티셔츠", 1));
        Category empty = categoryRepository.save(Category.root("하의", 2));

        assertThat(categoryRepository.existsByParentId(empty.getId()))
                .as("자식 없는 대분류")
                .isFalse();
        assertThat(categoryRepository.existsByParentId(child.getId()))
                .as("소분류는 2depth라 자식을 가질 수 없다")
                .isFalse();
    }

    @Test
    @DisplayName("createdAt이 auditing으로 채워진다 (NOT NULL이라 없으면 저장부터 깨진다)")
    void auditingFillsCreatedAt() {
        Category saved = categoryRepository.save(Category.root("상의", 1));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ── [Phase 3 · 7단계] findSelfAndChildIds ──────────────────────────────
    // @Query의 JPQL은 문자열이라 컴파일에서 검증되지 않는다. 실제 SQL이 도는 곳은 여기뿐이다.

    @Test
    @DisplayName("findSelfAndChildIds — 대분류를 주면 자기 자신과 소분류 id가 함께 나온다")
    void findSelfAndChildIds_root() {
        Category top = categoryRepository.save(Category.root("상의", 1));
        Category tshirt = categoryRepository.save(Category.child(top, "티셔츠", 1));
        Category shirt = categoryRepository.save(Category.child(top, "셔츠", 2));

        List<Long> ids = categoryRepository.findSelfAndChildIds(top.getId());

        assertThat(ids)
                .as("상품 필터의 IN 절에 그대로 들어갈 값이다")
                .containsExactlyInAnyOrder(top.getId(), tshirt.getId(), shirt.getId());
    }

    @Test
    @DisplayName("findSelfAndChildIds — 소분류를 주면 자기 자신 하나다 (2depth라 손자가 없다)")
    void findSelfAndChildIds_child() {
        Category top = categoryRepository.save(Category.root("상의", 1));
        Category tshirt = categoryRepository.save(Category.child(top, "티셔츠", 1));

        assertThat(categoryRepository.findSelfAndChildIds(tshirt.getId()))
                .containsExactly(tshirt.getId());
    }

    @Test
    @DisplayName("findSelfAndChildIds — 자식 없는 대분류도 자기 자신은 나온다")
    void findSelfAndChildIds_rootWithoutChildren() {
        Category bag = categoryRepository.save(Category.root("가방", 1));

        assertThat(categoryRepository.findSelfAndChildIds(bag.getId()))
                .containsExactly(bag.getId());
    }

    @Test
    @DisplayName("findSelfAndChildIds — 없는 id는 빈 목록이다 (서비스가 CA001로 바꾼다)")
    void findSelfAndChildIds_missing() {
        assertThat(categoryRepository.findSelfAndChildIds(99_999L))
                .as("존재하면 자기 자신이 반드시 걸리므로, 비었다는 것은 없다는 뜻이다")
                .isEmpty();
    }

    @Test
    @DisplayName("findSelfAndChildIds — 다른 대분류의 소분류는 섞이지 않는다")
    void findSelfAndChildIds_doesNotLeakSiblings() {
        Category top = categoryRepository.save(Category.root("상의", 1));
        Category tshirt = categoryRepository.save(Category.child(top, "티셔츠", 1));
        Category bottom = categoryRepository.save(Category.root("하의", 2));
        categoryRepository.save(Category.child(bottom, "슬랙스", 1));

        assertThat(categoryRepository.findSelfAndChildIds(top.getId()))
                .containsExactlyInAnyOrder(top.getId(), tshirt.getId());
    }
}

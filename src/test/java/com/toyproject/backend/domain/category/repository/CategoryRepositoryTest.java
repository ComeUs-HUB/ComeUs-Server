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
}

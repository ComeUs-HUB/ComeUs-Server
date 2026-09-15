package com.toyproject.backend.domain.category.entity;

import com.toyproject.backend.domain.category.exception.CategoryErrorCode;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 3 · 2단계] {@link Category} 깊이 불변식 검증
 *
 * <p>테이블은 self-join이라 <b>3depth 이상도 구조적으로 저장할 수 있다.</b> "2depth"는 스키마가
 * 아니라 코드가 지키는 규칙이고, 그 지점은 {@link Category#child(Category, String, int)} 하나다.
 * 그 검사를 지우면 무엇이 깨지는지를 여기서 못박는다 — 없으면 규칙을 지키는 것이 아무것도 없다.
 *
 * <p>Spring 컨텍스트도 DB도 필요 없다. 팩터리가 순수 함수이기 때문이며, 이 불변식이 영속성과
 * 무관하게 성립한다는 뜻이기도 하다.
 */
@DisplayName("Category")
class CategoryTest {

    private static final String ROOT_NAME = "상의";
    private static final String CHILD_NAME = "티셔츠";

    @Nested
    @DisplayName("2단계까지는 만들어진다")
    class WithinTwoDepth {

        @Test
        @DisplayName("대분류는 부모가 없다")
        void rootHasNoParent() {
            Category root = Category.root(ROOT_NAME, 1);

            assertThat(root.getParent()).isNull();
            assertThat(root.isRoot()).isTrue();
            assertThat(root.getName()).isEqualTo(ROOT_NAME);
            assertThat(root.getDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("대분류 아래 소분류가 붙는다")
        void childUnderRoot() {
            Category root = Category.root(ROOT_NAME, 1);

            Category child = Category.child(root, CHILD_NAME, 2);

            assertThat(child.getParent()).isSameAs(root);
            assertThat(child.isRoot())
                    .as("부모가 있으면 뿌리가 아니다 — 소분류는 다시 부모가 될 수 없다")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("3단계는 생성 지점에서 거부된다")
    class BeyondTwoDepth {

        @Test
        @DisplayName("소분류를 부모로 지정하면 CA003")
        void childUnderChildRejected() {
            Category root = Category.root(ROOT_NAME, 1);
            Category child = Category.child(root, CHILD_NAME, 1);

            assertThatThrownBy(() -> Category.child(child, "반팔티", 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED);
        }

        @Test
        @DisplayName("거부는 저장 전에 일어난다 — DB나 영속성 컨텍스트가 관여하지 않는다")
        void rejectedBeforePersistence() {
            Category root = Category.root(ROOT_NAME, 1);
            Category child = Category.child(root, CHILD_NAME, 1);

            assertThatThrownBy(() -> Category.child(child, "반팔티", 1))
                    .isInstanceOf(BusinessException.class);

            assertThat(child.getId())
                    .as("id가 없는 상태, 즉 트랜잭션 밖에서도 규칙이 성립한다")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("수정은 이름과 전시 순서만 바꾼다")
    class UpdateInfo {

        @Test
        @DisplayName("이름과 순서가 바뀐다")
        void changesNameAndOrder() {
            Category root = Category.root(ROOT_NAME, 1);

            root.updateInfo("아우터", 5);

            assertThat(root.getName()).isEqualTo("아우터");
            assertThat(root.getDisplayOrder()).isEqualTo(5);
        }

        @Test
        @DisplayName("부모는 바뀌지 않는다 — 부모 변경 수단 자체가 없어 깊이가 흔들릴 수 없다")
        void neverChangesParent() {
            Category root = Category.root(ROOT_NAME, 1);
            Category child = Category.child(root, CHILD_NAME, 1);

            child.updateInfo("셔츠", 2);

            assertThat(child.getParent()).isSameAs(root);
            assertThat(child.isRoot()).isFalse();
        }
    }
}

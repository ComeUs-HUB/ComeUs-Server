package com.toyproject.backend.domain.category.service;

import com.toyproject.backend.domain.category.dto.CategoryCreateRequest;
import com.toyproject.backend.domain.category.dto.CategoryResponse;
import com.toyproject.backend.domain.category.dto.CategoryTreeResponse;
import com.toyproject.backend.domain.category.dto.CategoryUpdateRequest;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.exception.CategoryErrorCode;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * [Phase 3 · 3단계] {@link CategoryService} 검증
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는 순수 단위 테스트</b>다. 이 단계의 판단은 트리 조립과
 * 분기(대분류/소분류, 삭제 가능 여부)이고 전부 DB 없이 드러난다. 실제 SQL(정렬·존재 확인)은
 * {@code CategoryRepositoryTest}가 이미 검증했다 — {@code UserServiceTest}와 같은 분업이다.
 *
 * <p>{@code id}를 {@link ReflectionTestUtils}로 심는 이유: 트리 조립이 부모 id로 묶기 때문에
 * id가 {@code null}이면 <b>모든 소분류가 한 덩어리로 묶여 테스트가 엉뚱하게 통과한다.</b>
 * 실제로는 JPA가 채우는 값이라 저장을 거치지 않는 이 테스트에서는 직접 심어야 한다
 * ({@code JwtProviderTest}가 쓰는 방식과 같다).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService")
class CategoryServiceTest {

    private static final Long TOP_ID = 1L;
    private static final Long BOTTOM_ID = 2L;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static Category root(Long id, String name, int displayOrder) {
        Category category = Category.root(name, displayOrder);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private static Category child(Long id, Category parent, String name, int displayOrder) {
        Category category = Category.child(parent, name, displayOrder);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Nested
    @DisplayName("트리 조회")
    class GetTree {

        @Test
        @DisplayName("대분류만 최상위에 남고 소분류는 그 아래로 들어간다")
        void nestsChildrenUnderRoots() {
            Category top = root(TOP_ID, "상의", 1);
            Category bottom = root(BOTTOM_ID, "하의", 2);
            given(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc()).willReturn(List.of(
                    top,
                    child(11L, top, "티셔츠", 1),
                    child(12L, top, "셔츠", 2),
                    bottom,
                    child(21L, bottom, "청바지", 1)));

            List<CategoryTreeResponse> tree = categoryService.getTree();

            assertThat(tree).extracting(CategoryTreeResponse::name)
                    .as("응답의 최상위는 대분류뿐이다")
                    .containsExactly("상의", "하의");
            assertThat(tree.get(0).children()).extracting(CategoryTreeResponse::name)
                    .containsExactly("티셔츠", "셔츠");
            assertThat(tree.get(1).children()).extracting(CategoryTreeResponse::name)
                    .containsExactly("청바지");
        }

        @Test
        @DisplayName("소분류의 children은 항상 비어 있다 — 2depth 계약이 응답에 드러난다")
        void childrenOfChildIsEmpty() {
            Category top = root(TOP_ID, "상의", 1);
            given(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
                    .willReturn(List.of(top, child(11L, top, "티셔츠", 1)));

            List<CategoryTreeResponse> tree = categoryService.getTree();

            assertThat(tree.get(0).children().get(0).children())
                    .as("null이 아니라 빈 리스트다 — 클라이언트가 null 검사 없이 순회한다")
                    .isEmpty();
        }

        @Test
        @DisplayName("조회는 1회뿐이다 — 깊이만큼 반복하지 않는다")
        void readsOnce() {
            Category top = root(TOP_ID, "상의", 1);
            given(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
                    .willReturn(List.of(top, child(11L, top, "티셔츠", 1)));

            categoryService.getTree();

            then(categoryRepository).should().findAllByOrderByDisplayOrderAscIdAsc();
            then(categoryRepository).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("자식 없는 대분류도 빈 children을 갖고 나온다")
        void rootWithoutChildren() {
            given(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
                    .willReturn(List.of(root(TOP_ID, "상의", 1)));

            List<CategoryTreeResponse> tree = categoryService.getTree();

            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).children()).isEmpty();
        }

        @Test
        @DisplayName("카테고리가 하나도 없으면 빈 목록이다 (실패가 아니다)")
        void emptyIsNotFailure() {
            given(categoryRepository.findAllByOrderByDisplayOrderAscIdAsc()).willReturn(List.of());

            assertThat(categoryService.getTree()).isEmpty();
        }
    }

    @Nested
    @DisplayName("생성 — parentId의 유무가 분기를 정한다")
    class Create {

        @Test
        @DisplayName("parentId가 없으면 대분류가 된다")
        void withoutParentIdCreatesRoot() {
            given(categoryRepository.save(any(Category.class))).willAnswer(i -> i.getArgument(0));

            CategoryResponse response = categoryService.create(new CategoryCreateRequest("상의", 1, null));

            assertThat(response.parentId()).isNull();
            assertThat(response.name()).isEqualTo("상의");
            then(categoryRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("parentId가 있으면 그 대분류 아래 소분류가 된다")
        void withParentIdCreatesChild() {
            given(categoryRepository.findById(TOP_ID)).willReturn(Optional.of(root(TOP_ID, "상의", 1)));
            given(categoryRepository.save(any(Category.class))).willAnswer(i -> i.getArgument(0));

            CategoryResponse response = categoryService.create(new CategoryCreateRequest("티셔츠", 1, TOP_ID));

            assertThat(response.parentId()).isEqualTo(TOP_ID);
            assertThat(response.name()).isEqualTo("티셔츠");
        }

        @Test
        @DisplayName("parentId가 가리키는 부모가 없으면 CA001")
        void parentNotFound() {
            given(categoryRepository.findById(TOP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("티셔츠", 1, TOP_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);

            then(categoryRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("부모가 이미 소분류면 CA003 — 서비스가 아니라 엔티티가 막는다")
        void parentIsNotRoot() {
            Category top = root(TOP_ID, "상의", 1);
            given(categoryRepository.findById(11L)).willReturn(Optional.of(child(11L, top, "티셔츠", 1)));

            assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("반팔티", 1, 11L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED);

            then(categoryRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("이름과 전시 순서가 바뀐다 — save를 다시 부르지 않는다 (더티 체킹)")
        void updatesViaDirtyChecking() {
            given(categoryRepository.findById(TOP_ID)).willReturn(Optional.of(root(TOP_ID, "상의", 1)));

            CategoryResponse response = categoryService.update(TOP_ID, new CategoryUpdateRequest("아우터", 5));

            assertThat(response.name()).isEqualTo("아우터");
            assertThat(response.displayOrder()).isEqualTo(5);
            then(categoryRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("소분류를 수정해도 부모는 그대로다")
        void keepsParent() {
            Category top = root(TOP_ID, "상의", 1);
            given(categoryRepository.findById(11L)).willReturn(Optional.of(child(11L, top, "티셔츠", 1)));

            CategoryResponse response = categoryService.update(11L, new CategoryUpdateRequest("셔츠", 2));

            assertThat(response.parentId())
                    .as("부모를 바꿀 수단 자체가 없어 깊이가 흔들릴 수 없다")
                    .isEqualTo(TOP_ID);
        }

        @Test
        @DisplayName("대상이 없으면 CA001")
        void notFound() {
            given(categoryRepository.findById(TOP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.update(TOP_ID, new CategoryUpdateRequest("아우터", 1)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("하위가 없으면 삭제된다 — 하드 삭제다")
        void deletesWhenNoChildren() {
            Category top = root(TOP_ID, "상의", 1);
            given(categoryRepository.findById(TOP_ID)).willReturn(Optional.of(top));
            given(categoryRepository.existsByParentId(TOP_ID)).willReturn(false);

            categoryService.delete(TOP_ID);

            then(categoryRepository).should().delete(top);
        }

        @Test
        @DisplayName("하위가 있으면 CA002 — 삭제가 실제로 일어나지 않는다")
        void rejectsWhenHasChildren() {
            given(categoryRepository.findById(TOP_ID)).willReturn(Optional.of(root(TOP_ID, "상의", 1)));
            given(categoryRepository.existsByParentId(TOP_ID)).willReturn(true);

            assertThatThrownBy(() -> categoryService.delete(TOP_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_HAS_CHILDREN);

            then(categoryRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("대상이 없으면 CA001 — 하위 확인보다 먼저 걸린다")
        void notFound() {
            given(categoryRepository.findById(TOP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.delete(TOP_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);

            then(categoryRepository).should(never()).existsByParentId(any());
        }
    }

    @Nested
    @DisplayName("[Phase 3 · 7단계] 하위 id 모으기 — 상품 필터가 쓴다")
    class CollectSelfAndChildIds {

        @Test
        @DisplayName("대분류면 자기 자신과 소분류 id가 함께 나온다")
        void rootReturnsSelfAndChildren() {
            given(categoryRepository.findSelfAndChildIds(1L)).willReturn(List.of(1L, 3L, 4L));

            assertThat(categoryService.collectSelfAndChildIds(1L))
                    .containsExactly(1L, 3L, 4L);
        }

        @Test
        @DisplayName("소분류면 자기 자신 하나다 — 2depth라 손자가 없다")
        void childReturnsSelfOnly() {
            given(categoryRepository.findSelfAndChildIds(3L)).willReturn(List.of(3L));

            assertThat(categoryService.collectSelfAndChildIds(3L)).containsExactly(3L);
        }

        @Test
        @DisplayName("빈 결과면 CA001 — 빈 목록으로 뭉개지 않는다")
        void emptyResultIsNotFound() {
            given(categoryRepository.findSelfAndChildIds(99L)).willReturn(List.of());

            assertThatThrownBy(() -> categoryService.collectSelfAndChildIds(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .as("존재하는 카테고리는 자기 자신이 반드시 걸리므로 빈 결과는 부재를 뜻한다")
                    .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);
        }

        @Test
        @DisplayName("존재 확인을 위한 조회를 따로 하지 않는다 — 쿼리 한 번으로 끝낸다")
        void doesNotQueryTwice() {
            given(categoryRepository.findSelfAndChildIds(1L)).willReturn(List.of(1L));

            categoryService.collectSelfAndChildIds(1L);

            // findById를 먼저 부르면 필요 없는 엔티티를 통째로 읽는 쿼리가 하나 더 붙는다
            then(categoryRepository).should(never()).findById(any());
        }
    }
}

package com.toyproject.backend.domain.category.service;

import com.toyproject.backend.domain.category.dto.CategoryCreateRequest;
import com.toyproject.backend.domain.category.dto.CategoryResponse;
import com.toyproject.backend.domain.category.dto.CategoryTreeResponse;
import com.toyproject.backend.domain.category.dto.CategoryUpdateRequest;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.exception.CategoryErrorCode;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [Phase 3 · 3단계] 카테고리 서비스. 트리 조회와 CRUD.
 *
 * <p>다른 도메인은 {@code CategoryRepository}가 아니라 이 클래스의 공개 메서드를 경유한다 —
 * 2depth 불변식이 {@link Category#child(Category, String, int)}에 있어서다. 같은 이유로
 * 깊이 검증을 서비스에서 다시 하지 않는다.
 *
 * <p>반환은 DTO다. 예외는 {@link #getCategory(Long)} — 상품이 FK 연관을 맺으려면
 * 영속 인스턴스 자체가 필요하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 도메인 간 역방향 참조다. 상품이 붙은 카테고리를 막는 이 검사 하나에만 쓰며,
     * 다른 용도로 넓히지 않는다(ADR 0014).
     */
    private final ProductRepository productRepository;

    /**
     * 전체 트리 조회. {@code GET /api/categories}
     *
     * <p>쿼리는 1회다. 대분류·소분류를 가리지 않고 전부 읽어 메모리에서 묶는다.
     * 2depth 고정이라 재귀 CTE도, 깊이만큼의 반복 조회도 필요 없다.
     *
     * <p>N+1이 나지 않는 이유: 묶는 데 쓰는 것은 {@code getParent().getId()}뿐인데,
     * 식별자는 LAZY 프록시가 이미 들고 있어 프록시를 초기화하지 않는다. 부모 엔티티를 실제로
     * 들여다보는 코드가 없으므로 추가 SELECT가 나가지 않는다.
     *
     * <p>결과는 대분류만 담은 목록이다. 소분류는 각 대분류의 {@code children} 안에 들어간다.
     * 부모가 조회 결과에 없는 고아 행은 나타날 수 없다 — FK가 보장한다.
     */
    public List<CategoryTreeResponse> getTree() {
        List<Category> all = categoryRepository.findAllByOrderByDisplayOrderAscIdAsc();

        Map<Long, List<CategoryTreeResponse>> childrenByParentId = groupChildrenByParentId(all);

        return all.stream()
                .filter(Category::isRoot)
                .map(root -> CategoryTreeResponse.of(
                        root,
                        childrenByParentId.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    /**
     * 카테고리 생성. {@code POST /api/categories}
     *
     * <p>{@code parentId}의 유무가 대분류/소분류를 가른다. 소분류 생성은
     * {@link Category#child(Category, String, int)}가 3depth를 거부한다 —
     * 여기서 깊이를 다시 검사하지 않는다.
     *
     * @throws BusinessException {@code parentId}가 가리키는 부모가 없으면 CA001(404),
     *                           그 부모가 이미 소분류이면 CA003(400)
     */
    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        Category category = (request.parentId() == null)
                ? Category.root(request.name(), request.displayOrder())
                : Category.child(getCategory(request.parentId()), request.name(), request.displayOrder());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    /**
     * 카테고리 수정. {@code PUT /api/categories/{id}}
     *
     * <p>이름과 전시 순서만 바꾼다. 부모는 요청 DTO에 필드조차 없다
     * ({@code CategoryUpdateRequest} Javadoc 참고).
     *
     * @throws BusinessException 대상이 없으면 CA001(404)
     */
    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = getCategory(id);
        category.updateInfo(request.name(), request.displayOrder());

        return CategoryResponse.from(category);
    }

    /**
     * 카테고리 삭제(하드 삭제). 하위 카테고리와 붙어 있는 상품이 둘 다 없어야 한다.
     *
     * <p>상품 검사가 자기 자신만 보는 것은 CA002가 먼저 막아 잎만 도달하기 때문이다(ADR 0014).
     *
     * @throws BusinessException 대상이 없으면 CA001(404), 하위 카테고리가 있으면 CA002(409),
     *                           상품이 붙어 있으면 CA004(409)
     */
    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);

        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_HAS_PRODUCTS);
        }
        categoryRepository.delete(category);
    }

    /**
     * 자기 자신과 하위 카테고리의 id를 모은다. 상품 목록 필터가 쓰는 진입점이다.
     *
     * <p>2depth 고정이라 재귀가 없다. 존재 확인을 따로 하지 않는 것은 존재하는 카테고리가
     * 질의에 반드시 걸려 빈 결과가 곧 "없는 카테고리"이기 때문이다 — 조회 한 번으로
     * CA001 판정까지 끝난다.
     *
     * @throws BusinessException 그런 카테고리가 없으면 CA001(404). 빈 목록으로 뭉개지 않는다
     */
    public List<Long> collectSelfAndChildIds(Long categoryId) {
        List<Long> ids = categoryRepository.findSelfAndChildIds(categoryId);

        if (ids.isEmpty()) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND);
        }
        return ids;
    }

    /**
     * id로 카테고리를 읽는다. 없으면 CA001.
     *
     * <p>{@code ProductService}가 상품 등록·수정에서 {@code Category}를 얻는 진입점이다 —
     * {@code CategoryRepository}는 category 패키지 밖에서 주입하지 않는다.
     *
     * <p>{@code UserService.getActiveUser}와 달리 404가 맞다. 클라이언트가 경로 변수나
     * 요청 바디로 카테고리를 직접 지목하므로 "네가 지목한 것이 없다"가 정확한 설명이다.
     */
    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * 소분류를 부모 id로 묶는다.
     *
     * <p>{@code LinkedHashMap}과 {@code ArrayList}로 조회 순서를 보존한다. 입력이 이미
     * {@code displayOrder}(동률이면 {@code id}) 순으로 정렬돼 있으므로, 순서를 유지하기만 하면
     * 각 대분류 아래 소분류도 전시 순서대로 남는다. 여기서 다시 정렬할 필요가 없다.
     */
    private Map<Long, List<CategoryTreeResponse>> groupChildrenByParentId(List<Category> all) {
        Map<Long, List<CategoryTreeResponse>> grouped = new LinkedHashMap<>();

        for (Category category : all) {
            if (category.isRoot()) {
                continue;
            }
            grouped.computeIfAbsent(category.getParent().getId(), key -> new ArrayList<>())
                    .add(CategoryTreeResponse.of(category));
        }
        return grouped;
    }
}

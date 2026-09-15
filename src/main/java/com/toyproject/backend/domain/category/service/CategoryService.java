package com.toyproject.backend.domain.category.service;

import com.toyproject.backend.domain.category.dto.CategoryCreateRequest;
import com.toyproject.backend.domain.category.dto.CategoryResponse;
import com.toyproject.backend.domain.category.dto.CategoryTreeResponse;
import com.toyproject.backend.domain.category.dto.CategoryUpdateRequest;
import com.toyproject.backend.domain.category.entity.Category;
import com.toyproject.backend.domain.category.exception.CategoryErrorCode;
import com.toyproject.backend.domain.category.repository.CategoryRepository;
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
 * [Phase 3 · 3단계] 카테고리 서비스
 *
 * <p><b>역할</b><br>
 * 카테고리 트리 조회와 CRUD. <b>카테고리를 만드는 규칙은 전부 여기와 {@link Category}에 있다</b> —
 * 다른 도메인(product)은 {@code CategoryRepository}가 아니라 이 클래스의 공개 메서드만 경유한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>깊이 검증을 <b>여기서 하지 않는다.</b> {@link Category#child(Category, String, int)}가 이미 막고 있고,
 *       서비스가 같은 검사를 한 번 더 하면 규칙이 두 곳으로 갈라진다.</li>
 *   <li>상태 변경은 엔티티의 도메인 메서드({@code updateInfo})에 위임하고 더티 체킹으로 반영한다.
 *       서비스에서 필드를 직접 세팅하거나 {@code save}를 다시 부르지 않는다 — {@code UserService}와 같다.</li>
 *   <li>엔티티를 그대로 반환하지 않고 DTO로 바꿔 내보낸다.</li>
 * </ul>
 *
 * <p><b>삭제는 하드 삭제다.</b> 상품({@code HIDDEN} soft delete)과 다르다 — 카테고리는 주문 이력에
 * 매달리지 않아 지워도 과거 기록이 깨지지 않는다.
 *
 * <p>⚠️ <b>"상품이 붙은 카테고리 삭제"는 아직 막지 않는다.</b> {@code Product}가 없어 FK 자체가 없다.
 * Phase 3-2에서 {@code products.category_id}가 생기면 그때 함께 막아야 한다 —
 * 지금 걸 수 있는 것은 하위 카테고리 존재(CA002)뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 전체 트리 조회. {@code GET /api/categories}
     *
     * <p><b>쿼리는 1회다.</b> 대분류·소분류를 가리지 않고 전부 읽어 메모리에서 묶는다.
     * 2depth 고정이라 재귀 CTE도, 깊이만큼의 반복 조회도 필요 없다.
     *
     * <p><b>N+1이 나지 않는 이유</b>: 묶는 데 쓰는 것은 {@code getParent().getId()}뿐인데,
     * 식별자는 LAZY 프록시가 이미 들고 있어 프록시를 초기화하지 않는다. 부모 엔티티를 실제로
     * 들여다보는 코드가 없으므로 추가 SELECT가 나가지 않는다.
     *
     * <p>결과는 <b>대분류만</b> 담은 목록이다. 소분류는 각 대분류의 {@code children} 안에 들어간다.
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
     * {@link Category#child(Category, String, int)}가 <b>3depth를 거부</b>하므로 여기서 깊이를 검사하지 않는다.
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
     * <p><b>이름과 전시 순서만 바꾼다.</b> 부모는 요청 DTO에 필드조차 없다
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
     * 카테고리 삭제. {@code DELETE /api/categories/{id}}
     *
     * <p>하위 카테고리가 하나라도 있으면 거부한다. <b>2depth라 손자를 따질 필요가 없어</b>
     * 이 한 번의 검사로 끝난다 — 깊이가 무제한이었다면 하위 전체를 훑어야 했다.
     *
     * @throws BusinessException 대상이 없으면 CA001(404), 하위 카테고리가 있으면 CA002(409)
     */
    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);

        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_HAS_CHILDREN);
        }
        categoryRepository.delete(category);
    }

    /**
     * 소분류를 부모 id로 묶는다.
     *
     * <p>{@code LinkedHashMap}과 {@code ArrayList}로 <b>조회 순서를 보존</b>한다. 입력이 이미
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

    /**
     * id로 카테고리를 읽는다. 없으면 CA001.
     *
     * <p>{@code UserService.getActiveUser}와 달리 <b>404가 맞다.</b> 클라이언트가 경로 변수로
     * 카테고리를 <b>직접 지목</b>하므로 "네가 지목한 것이 없다"가 정확한 설명이다.
     * 회원 API는 지목 수단이 없어 401로 내렸지만 여기는 사정이 다르다.
     */
    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }
}

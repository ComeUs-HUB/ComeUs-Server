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
 *   <li>엔티티를 그대로 반환하지 않고 DTO로 바꿔 내보낸다. 예외는 {@link #getCategory(Long)}이며,
 *       상품이 FK 연관을 맺으려면 영속 상태의 인스턴스 자체가 필요해서다.</li>
 * </ul>
 *
 * <p><b>삭제는 하드 삭제다.</b> 상품({@code HIDDEN} soft delete)과 다르다 — 카테고리는 주문 이력에
 * 매달리지 않아 지워도 과거 기록이 깨지지 않는다.
 *
 * <p>⚠️ <b>"상품이 붙은 카테고리 삭제"를 아직 CA004로 막지 않는다.</b> FK(RESTRICT)가 삭제 자체는
 * 거부하지만 응답이 C005(409)로 나간다. 8단계에서 {@code ProductRepository}를 직접 읽어
 * CA004로 바꾼다(ADR 0014) — 지금 여기서 거는 것은 하위 카테고리 존재(CA002)뿐이다.
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
     * 자기 자신과 하위 카테고리의 id를 모은다. <b>상품 목록의 카테고리 필터가 쓴다.</b>
     *
     * <p><b>이 메서드가 product 도메인의 진입점이다.</b> {@code ProductService}는
     * {@code CategoryRepository}를 직접 주입하지 않고 여기를 경유한다 — 그래야 "대분류를 고르면
     * 하위까지 포함한다"는 규칙과 2depth 전제가 category 도메인 안에 남는다.
     *
     * <p>대분류를 넘기면 {@code [자기 자신 + 소분류들]}, 소분류를 넘기면 {@code [자기 자신]}이다.
     * <b>2depth 고정이라 재귀가 없다</b> — 손자가 존재할 수 없기 때문이다.
     *
     * <p><b>존재 확인을 따로 하지 않는 이유</b><br>
     * 존재하는 카테고리는 질의의 {@code c.id = :categoryId}에 반드시 걸려 결과가 최소 1개다.
     * 그래서 <b>빈 결과가 곧 "없는 카테고리"</b>이고, 조회 한 번으로 CA001 판정까지 끝난다.
     * {@link #getCategory(Long)}를 먼저 부르면 엔티티를 통째로 읽는 쿼리가 하나 더 붙는데,
     * 여기서 필요한 것은 id뿐이다.
     *
     * @param categoryId 클라이언트가 지정한 카테고리 id
     * @throws BusinessException 그런 카테고리가 없으면 CA001(404).
     *                           빈 목록으로 뭉개지 않는다 — 없는 것을 지목한 요청과
     *                           상품이 하나도 없는 카테고리는 다르다
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
     * <p><b>{@code ProductService}가 상품 등록·수정에서 {@code Category}를 얻는 진입점이다</b> —
     * {@code CategoryRepository}는 category 패키지 밖에서 주입하지 않는다.
     *
     * <p>{@code UserService.getActiveUser}와 달리 <b>404가 맞다.</b> 클라이언트가 경로 변수나
     * 요청 바디로 카테고리를 <b>직접 지목</b>하므로 "네가 지목한 것이 없다"가 정확한 설명이다.
     */
    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
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
}

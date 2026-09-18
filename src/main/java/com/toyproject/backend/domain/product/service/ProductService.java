package com.toyproject.backend.domain.product.service;

import com.toyproject.backend.domain.category.service.CategoryService;
import com.toyproject.backend.domain.product.dto.ProductDetailResponse;
import com.toyproject.backend.domain.product.dto.ProductSearchCondition;
import com.toyproject.backend.domain.product.dto.ProductSearchRequest;
import com.toyproject.backend.domain.product.dto.ProductListResponse;
import com.toyproject.backend.domain.product.entity.Product;
import com.toyproject.backend.domain.product.entity.ProductStatus;
import com.toyproject.backend.domain.product.exception.ProductErrorCode;
import com.toyproject.backend.domain.product.repository.ProductRepository;
import com.toyproject.backend.domain.product.repository.ProductSpecifications;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * [Phase 3 · 7단계] 상품 서비스
 *
 * <p><b>역할</b><br>
 * 상품 조회 유스케이스. <b>지금은 읽기 둘뿐</b>이고, 등록·수정·삭제는 8단계에서 붙는다.
 *
 * <p><b>설계</b> — {@code CategoryService}와 같은 골격이다.
 * <ul>
 *   <li>클래스 기본이 {@code @Transactional(readOnly = true)}. 쓰기 메서드가 생기면 그 메서드에만
 *       {@code @Transactional}을 붙인다.</li>
 *   <li>엔티티를 그대로 반환하지 않고 DTO로 바꿔 내보낸다.</li>
 *   <li>{@code @Slf4j}는 두되 로그는 컨트롤러에서 남긴다 — 조회는 기록할 상태 변화가 없다.</li>
 * </ul>
 *
 * <p><b>⚠️ 카테고리는 {@code CategoryService}를 경유한다</b><br>
 * {@code CategoryRepository}를 직접 주입하지 않는다. 그 Repository에
 * "category 패키지 안에서만 주입한다"는 규칙이 걸려 있고, 그래야 "대분류를 고르면 하위까지"라는
 * 규칙과 2depth 전제가 category 도메인 안에 남는다.
 *
 * <p>반대 방향인 {@code CategoryService → ProductRepository}는 순환을 끊기 위한 예외이며
 * 8단계에서 CA004와 함께 생긴다(ADR 0014). <b>두 방향이 대칭이 아니다.</b>
 *
 * <p><b>HIDDEN은 읽기 경로에서만 걸린다</b><br>
 * {@link #getList}는 {@code ProductSpecifications.search()} 안의 붙박이 필터가,
 * {@link #getDetail}은 {@code findByIdAndStatusNot}이 거른다. 8단계의 수정·삭제는
 * {@code findById}로 HIDDEN까지 읽어야 하므로 <b>이 두 조회와 같은 헬퍼로 합치지 않는다</b>(ADR 0013).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    /**
     * 상품 목록 조회. {@code GET /api/products}
     *
     * <p>흐름
     * 1. 부모 카테고리를 자식 카테고리로 펼치기
     * 2. 조건 조립(카테고리Ids, 최대가격, 최소가격, 검색키워드)
     * 3. 페이지를 DTO로 변경.
     *
     * 필터를 WHERE 절로 바꾸는 일은 전부 {@code ProductSpecifications}가 하므로
     * 여기에 조건 분기가 없다.
     *
     * @param request  클라이언트가 보낸 그대로의 검색 조건({@code categoryId}가 단수
     * @param pageable 페이지·크기·정렬. 상한과 정렬 화이트리스트는 컨트롤러가 이미 걸러 준다
     * @throws BusinessException {@code categoryId}가 가리키는 카테고리가 없으면 CA001(404)
     */
    public PageResponse<ProductListResponse> getList(ProductSearchRequest request, Pageable pageable) {
        ProductSearchCondition condition = new ProductSearchCondition(
                expandCategoryIds(request.categoryId()),
                request.minPrice(),
                request.maxPrice(),
                request.keyword());

        Page<Product> page = productRepository.findAll(
                ProductSpecifications.search(condition), pageable);

        return PageResponse.of(page.map(ProductListResponse::from));
    }

    /**
     * 상품 상세 조회. {@code GET /api/products/{id}}
     *
     * <p>숨긴 상품은 <b>없는 것과 같게</b> 취급한다 — 목록에서 빠지는 것과 같은 기준이며,
     * 권한으로 분기하지 않으므로 ADMIN이 호출해도 결과가 같다(ADR 0013).
     *
     * @throws BusinessException 없거나 숨긴 상품이면 P001(404)
     */
    public ProductDetailResponse getDetail(Long id) {
        Product product = productRepository.findByIdAndStatusNot(id, ProductStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return ProductDetailResponse.from(product);
    }

    /**
     * 카테고리 하나를 {@code [자기 자신 + 자식들]}로 펼친다.
     *
     * <p><b>여기가 단수 → 복수로 바뀌는 지점이다.</b> {@code ProductSearchRequest}는 단수
     * {@code categoryId}를, {@code ProductSearchCondition}은 펼쳐진 목록을 들고 있고,
     * 그 경계가 이 한 줄이다.
     *
     * <p>{@code categoryId}가 없으면 조회하지 않고 {@code null}을 돌려준다 —
     * <b>카테고리 필터를 쓰지 않는 요청에서 쿼리 한 번을 아낀다.</b>
     * {@code null}은 {@code ProductSpecifications}에서 "조건 없음"으로 흘러간다.
     */
    private List<Long> expandCategoryIds(Long categoryId) {
        return (categoryId == null) ? null : categoryService.collectSelfAndChildIds(categoryId);
    }
}

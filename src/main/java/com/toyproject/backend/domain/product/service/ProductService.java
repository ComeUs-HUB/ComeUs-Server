package com.toyproject.backend.domain.product.service;

import com.toyproject.backend.domain.category.service.CategoryService;
import com.toyproject.backend.domain.product.dto.ProductCreateRequest;
import com.toyproject.backend.domain.product.dto.ProductDetailResponse;
import com.toyproject.backend.domain.product.dto.ProductListResponse;
import com.toyproject.backend.domain.product.dto.ProductSearchCondition;
import com.toyproject.backend.domain.product.dto.ProductSearchRequest;
import com.toyproject.backend.domain.product.dto.ProductUpdateRequest;
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
 * [Phase 3 · 7단계] 상품 서비스. 조회 둘과 쓰기 셋을 갖는다.
 *
 * <p>카테고리는 {@code CategoryService}를 경유한다 — {@code CategoryRepository}에
 * "category 패키지 안에서만 주입한다"는 규칙이 걸려 있다. 반대 방향
 * ({@code CategoryService → ProductRepository})은 순환을 끊기 위한 예외이며 대칭이 아니다(ADR 0014).
 *
 * <p>{@code HIDDEN}은 읽기 경로에서만 걸린다. 수정·삭제는 {@link #getProduct(Long)}으로
 * 숨김까지 읽는다 — 그것이 되살리는 유일한 통로라 조회 경로와 합치지 않는다(ADR 0013).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    /**
     * 상품 목록 조회. {@code PageResponse}의 첫 실사용처다.
     *
     * @param pageable 상한과 정렬 화이트리스트는 컨트롤러가 이미 걸러 준다
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
     * 상품 상세 조회. 숨긴 상품은 없는 것과 같게 취급하며 권한으로 분기하지 않는다.
     *
     * @throws BusinessException 없거나 숨긴 상품이면 P001(404)
     */
    public ProductDetailResponse getDetail(Long id) {
        return ProductDetailResponse.from(getVisibleProduct(id));
    }

    /**
     * [Phase 4 · 2단계] 노출 상품 엔티티 조회. 장바구니 담기가 쓴다.
     *
     * @throws BusinessException 없거나 숨긴 상품이면 P001(404)
     */
    public Product getVisibleProduct(Long id) {
        return productRepository.findByIdAndStatusNot(id, ProductStatus.HIDDEN)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 상품 등록. 재고 0이면 {@code SOLD_OUT}으로 시작한다.
     *
     * @throws BusinessException {@code categoryId}가 가리키는 카테고리가 없으면 CA001(404)
     */
    @Transactional
    public ProductDetailResponse create(ProductCreateRequest request) {
        Product product = Product.create(
                categoryService.getCategory(request.categoryId()),
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity());
        product.replaceImages(request.imageUrls());

        log.info("상품 등록 — categoryId={}, name={}", request.categoryId(), request.name());

        return ProductDetailResponse.from(productRepository.save(product));
    }

    /**
     * 상품 수정. 전체 교체이며 {@code HIDDEN}을 푼다(ADR 0013).
     *
     * @throws BusinessException 대상이 없으면 P001(404), 카테고리가 없으면 CA001(404)
     */
    @Transactional
    public ProductDetailResponse update(Long id, ProductUpdateRequest request) {
        Product product = getProduct(id);

        product.updateInfo(
                categoryService.getCategory(request.categoryId()),
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity());
        product.replaceImages(request.imageUrls());

        return ProductDetailResponse.from(product);
    }

    /**
     * 상품 삭제. 행을 지우지 않고 {@code HIDDEN}으로 바꾼다 — 주문 이력이 매달린다.
     *
     * @throws BusinessException 대상이 없으면 P001(404)
     */
    @Transactional
    public void delete(Long id) {
        getProduct(id).hide();

        log.info("상품 삭제(soft) — id={}", id);
    }

    /**
     * 수정·삭제 전용 조회. {@code findByIdAndStatusNot}과 달리 {@code HIDDEN}까지 읽는다.
     *
     * @throws BusinessException 없으면 P001(404)
     */
    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 카테고리 하나를 {@code [자기 자신 + 자식들]}로 펼친다. <b>단수가 복수로 바뀌는 지점이다.</b>
     *
     * <p>{@code categoryId}가 없으면 조회하지 않고 {@code null}을 돌려준다 — 쿼리 한 번을 아끼고,
     * {@code null}은 Specifications에서 "조건 없음"으로 흘러간다.
     */
    private List<Long> expandCategoryIds(Long categoryId) {
        return (categoryId == null) ? null : categoryService.collectSelfAndChildIds(categoryId);
    }
}

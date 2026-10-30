package com.toyproject.backend.domain.product.controller;

import com.toyproject.backend.domain.product.dto.ProductCreateRequest;
import com.toyproject.backend.domain.product.dto.ProductDetailResponse;
import com.toyproject.backend.domain.product.dto.ProductListResponse;
import com.toyproject.backend.domain.product.dto.ProductSearchRequest;
import com.toyproject.backend.domain.product.dto.ProductUpdateRequest;
import com.toyproject.backend.domain.product.service.ProductService;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.exception.CommonErrorCode;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;

/**
 * [Phase 3 · 8단계] 상품 API. 조회 둘은 비회원에게 열려 있고 쓰기 셋은 ADMIN만 호출한다.
 *
 * <p>{@code @AuthenticationPrincipal}을 받지 않는다 — 상품에는 소유자가 없고 HIDDEN을 권한으로
 * 분기하지 않기로 했으므로(ADR 0013) 누가 호출했는지가 동작을 바꾸지 않는다.
 * 권한 판정은 필터체인에서 끝난다.
 *
 * <p>{@code ResponseEntity}는 헤더나 200 이외 상태가 필요할 때만 쓴다
 * (conventions/api-response.md) — 다섯 중 {@code POST} 하나다.
 */
@Slf4j
@RestController
@RequestMapping(ProductController.BASE_PATH)
@RequiredArgsConstructor
public class ProductController {

    static final String BASE_PATH = "/api/products";

    /** 초과 요청은 거절하지 않고 잘라낸다 — 목록을 못 받는 것보다 낫다. */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 허용 정렬 필드. 밖의 값은 Spring Data가 {@code PropertyReferenceException}을 던지고
     * 이 예외는 {@code GlobalExceptionHandler}에 없어 C003(500)으로 새므로,
     * {@link #sanitize(Pageable)}가 먼저 C001(400)로 막는다.
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "price", "name");

    private final ProductService productService;

    /**
     * 상품 목록. 비회원도 호출할 수 있다.
     *
     * <p>필터가 하나도 없거나 결과가 0건이어도 실패가 아니다 — 빈 {@code content}가 나간다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ProductListResponse>> getList(
            @Valid @ModelAttribute ProductSearchRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(productService.getList(request, sanitize(pageable)));
    }

    /**
     * 상품 상세. 없거나 숨긴 상품이면 P001(404)이며 둘을 구분하지 않는다.
     *
     * <p>숫자가 아닌 경로 변수는 {@code GlobalExceptionHandler}가 C001(400)으로 처리한다.
     */
    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.success(productService.getDetail(id));
    }

    /**
     * 상품 등록. 201 + {@code Location}으로 응답한다.
     *
     * <p>{@code Location}은 절대 URL이 아닌 상대 경로다 — {@code CategoryController}와 같다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductDetailResponse>> create(
            @Valid @RequestBody ProductCreateRequest request) {
        ProductDetailResponse response = productService.create(request);

        return ResponseEntity
                .created(URI.create(BASE_PATH + "/" + response.id()))
                .body(ApiResponse.success(response));
    }

    /**
     * 상품 수정. 부분 수정이 아니라 전체 교체이며 {@code HIDDEN}을 푼다(ADR 0013).
     */
    @PutMapping("/{id}")
    public ApiResponse<ProductDetailResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.success(productService.update(id, request));
    }

    /**
     * 상품 삭제(soft delete). 200 + {@code data: null} — 204면 공통 응답 포맷이 깨진다.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);

        return ApiResponse.success(null);
    }

    /**
     * 정렬 필드를 검증하고, 페이지 크기를 자르고, {@code id}를 tie-breaker로 붙인다.
     *
     * <p>{@code price}·{@code name}은 물론 기본값인 {@code createdAt}도 동률이 생긴다.
     * 전순서가 아니면 페이지를 넘길 때 같은 상품이 두 번 나오거나 빠진다 —
     * 카테고리가 {@code displayOrder} 다음에 {@code id}를 두는 것과 같은 이유다.
     *
     * @throws BusinessException 허용되지 않은 정렬 필드면 C001(400)
     */
    private Pageable sanitize(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
            }
        }

        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort().and(Sort.by("id")));
    }
}

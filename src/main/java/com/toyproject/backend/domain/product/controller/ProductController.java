package com.toyproject.backend.domain.product.controller;

import com.toyproject.backend.domain.product.dto.ProductDetailResponse;
import com.toyproject.backend.domain.product.dto.ProductListResponse;
import com.toyproject.backend.domain.product.dto.ProductSearchRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * [Phase 3 · 8단계] 상품 API. 지금은 조회 둘뿐이고 쓰기 셋은 이어서 붙는다.
 *
 * <p>{@code @AuthenticationPrincipal}을 받지 않는다 — 상품에는 소유자가 없고 HIDDEN을 권한으로
 * 분기하지 않기로 했으므로(ADR 0013) 누가 호출했는지가 동작을 바꾸지 않는다.
 * 권한 판정은 필터체인에서 끝난다.
 *
 * <p>둘 다 헤더나 200 이외 상태를 다루지 않으므로 {@code ResponseEntity}를 쓰지 않는다
 * (conventions/api-response.md).
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
     * 정렬 필드를 검증하고 페이지 크기를 상한으로 자른다.
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

        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}

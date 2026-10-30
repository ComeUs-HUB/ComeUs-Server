package com.toyproject.backend.domain.category.controller;

import com.toyproject.backend.domain.category.dto.CategoryCreateRequest;
import com.toyproject.backend.domain.category.dto.CategoryResponse;
import com.toyproject.backend.domain.category.dto.CategoryTreeResponse;
import com.toyproject.backend.domain.category.dto.CategoryUpdateRequest;
import com.toyproject.backend.domain.category.service.CategoryService;
import com.toyproject.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * [Phase 3 · 4단계] 카테고리 트리 조회와 CRUD의 HTTP 계약. 판단은 서비스와 엔티티에 있다.
 *
 * <p>{@code @AuthenticationPrincipal}을 받지 않는다 — 카테고리에 소유자 개념이 없어
 * 누가 호출했는지가 동작을 바꾸지 않는다. 권한 판정은 필터체인에서 끝난다.
 * <p>{@code ResponseEntity}는 헤더나 200 이외 상태가 필요할 때만 쓴다
 * ({@code docs/conventions/api-response.md}). 넷 중 {@code POST}만 해당한다.
 * <p>엔드포인트·권한 계약은 {@code docs/domains/catalog/category.md}.
 */
@Slf4j
@RestController
@RequestMapping(CategoryController.BASE_PATH)
@RequiredArgsConstructor
public class CategoryController {

    static final String BASE_PATH = "/api/categories";

    private final CategoryService categoryService;

    /**
     * 전체 카테고리 트리. 비회원도 호출할 수 있다.
     *
     * <p>카테고리가 하나도 없으면 빈 배열이 나간다 — 실패가 아니다.
     */
    @GetMapping
    public ApiResponse<List<CategoryTreeResponse>> getTree() {
        return ApiResponse.success(categoryService.getTree());
    }

    /**
     * 카테고리 생성. 201 + {@code Location}으로 응답한다.
     *
     * <p>{@code Location}을 절대 URL이 아니라 상대 경로로 만든다.
     * {@code ServletUriComponentsBuilder}로 절대 URL을 조립하면 프록시 뒤에서 호스트·스킴이
     * 어긋날 수 있고({@code X-Forwarded-*} 처리에 의존하게 된다), 클라이언트가 필요한 것은
     * "방금 만든 것이 어디 있는가"뿐이라 상대 경로로 충분하다.
     *
     * @param request {@code parentId}가 없으면 대분류, 있으면 그 아래 소분류
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.create(request);

        log.info("카테고리 생성 — id={}, parentId={}", response.id(), response.parentId());

        return ResponseEntity
                .created(URI.create(BASE_PATH + "/" + response.id()))
                .body(ApiResponse.success(response));
    }

    /**
     * 카테고리 수정. 이름과 전시 순서만 바뀐다 — 요청 DTO에 부모 필드가 없다.
     */
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.update(id, request));
    }

    /**
     * 카테고리 삭제. 하위 카테고리가 있으면 CA002(409)로 거절된다.
     *
     * <p>응답은 200 + {@code data: null}이다. 204를 쓰지 않는 이유는 바디가 없으면
     * 공통 포맷이 깨지기 때문이다({@code docs/conventions/api-response.md}).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);

        log.info("카테고리 삭제 — id={}", id);

        return ApiResponse.success(null);
    }
}

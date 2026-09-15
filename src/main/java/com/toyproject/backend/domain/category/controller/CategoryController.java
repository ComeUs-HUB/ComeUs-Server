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
 * [Phase 3 · 4단계] 카테고리 API
 *
 * <p><b>역할</b><br>
 * 카테고리 트리 조회와 CRUD의 HTTP 계약. 판단은 전부 {@code CategoryService}와 {@code Category}에 있고,
 * 여기서는 <b>어떤 상태 코드와 헤더로 내보낼지</b>만 정한다.
 *
 * <p><b>API</b>
 * <table border="1">
 *   <caption>엔드포인트</caption>
 *   <tr><th>Method</th><th>URI</th><th>권한</th><th>응답</th></tr>
 *   <tr><td>GET</td><td>{@code /api/categories}</td><td><b>비회원</b></td>
 *       <td>200 · 대분류 배열에 소분류가 중첩된 트리</td></tr>
 *   <tr><td>POST</td><td>{@code /api/categories}</td><td>ADMIN</td>
 *       <td><b>201</b> + {@code Location}</td></tr>
 *   <tr><td>PUT</td><td>{@code /api/categories/{id}}</td><td>ADMIN</td><td>200</td></tr>
 *   <tr><td>DELETE</td><td>{@code /api/categories/{id}}</td><td>ADMIN</td>
 *       <td>200 + {@code data: null}</td></tr>
 * </table>
 *
 * <p><b>이 컨트롤러가 처음 하는 것 둘</b>
 * <ul>
 *   <li><b>비회원에게 열린 첫 API다.</b> Phase 2까지 모든 엔드포인트가
 *       {@code anyRequest().authenticated()}에 걸려 있었다. permitAll은 {@code SecurityConfig}가 건다.</li>
 *   <li><b>경로 변수를 쓰는 첫 API다.</b> {@code /api/users/me}에는 경로 변수가 없었다.
 *       {@code /api/categories/abc}처럼 타입이 맞지 않는 요청은
 *       {@code GlobalExceptionHandler}가 C001(400)로 처리한다 — 그 핸들러가 없으면 C003(500)이 된다.</li>
 * </ul>
 *
 * <p><b>설계</b>
 * <ul>
 *   <li><b>{@code @AuthenticationPrincipal}을 받지 않는다.</b> 카테고리에는 소유자 개념이 없어
 *       누가 호출했는지가 동작을 바꾸지 않는다 — 권한 판정은 필터체인에서 끝난다.
 *       {@code UserController}가 principal을 받는 것은 "내 정보"이기 때문이고, 여기는 사정이 다르다.</li>
 *   <li>반환 타입은 <b>헤더나 200 이외 상태가 필요할 때만</b> {@code ResponseEntity}다
 *       (conventions/api-response.md). 넷 중 {@code POST}만 해당한다.</li>
 *   <li>요청 바디 검증은 {@code @Valid}가 형식만 본다. 실패는 {@code GlobalExceptionHandler}가 C001로 처리한다.
 *       도메인 규칙(2depth·하위 존재)은 서비스와 엔티티가 {@code BusinessException}으로 던진다.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping(CategoryController.BASE_PATH)
@RequiredArgsConstructor
public class CategoryController {

    static final String BASE_PATH = "/api/categories";

    private final CategoryService categoryService;

    /**
     * 전체 카테고리 트리. <b>비회원도 호출할 수 있다.</b>
     *
     * <p>카테고리가 하나도 없으면 빈 배열이 나간다 — 실패가 아니다.
     */
    @GetMapping
    public ApiResponse<List<CategoryTreeResponse>> getTree() {
        return ApiResponse.success(categoryService.getTree());
    }

    /**
     * 카테고리 생성. <b>201 + {@code Location}</b>으로 응답한다.
     *
     * <p>{@code Location}을 절대 URL이 아니라 <b>상대 경로</b>로 만든다.
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
     * 카테고리 수정. <b>이름과 전시 순서만 바뀐다</b> — 요청 DTO에 부모 필드가 없다.
     */
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.update(id, request));
    }

    /**
     * 카테고리 삭제. 하위 카테고리가 있으면 CA002(409)로 거절된다.
     *
     * <p>응답은 <b>200 + {@code data: null}</b>이다. 204를 쓰지 않는 이유는 바디가 없으면
     * 공통 {@code {success, data, error}} 포맷이 깨지기 때문이다(conventions/api-response.md).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);

        log.info("카테고리 삭제 — id={}", id);

        return ApiResponse.success(null);
    }
}

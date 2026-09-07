# 응답 포맷

모든 API 응답이 지키는 구조와, 예외가 그 구조로 변환되는 경로를 정의한다.
에러 코드 값 자체는 [error-codes.md](error-codes.md)가 소유한다.

## `ApiResponse<T>`

`global/response/ApiResponse.java`

```java
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(true, data, null); }
    public static ApiResponse<Void> error(ErrorResponse error) { return new ApiResponse<>(false, null, error); }
}
```

```json
// 성공
{ "success": true,  "data": { "id": 1 }, "error": null }
// 실패
{ "success": false, "data": null, "error": { "code": "A007", "message": "...", "errors": null } }
```

- `data`와 `error`는 **항상 배타적**이다. 동시에 값이 있으면 안 된다.
- **인자 없는 `success()` 정적 팩토리는 만들지 않는다.** record 컴포넌트 `success`(boolean)의 자동 생성
  접근자 `success()`와 시그니처가 충돌해 컴파일 에러가 난다(static/instance 무관). 데이터 없는 성공 응답은
  `ApiResponse.<Void>success(null)`로 반환한다.
- 삭제(DELETE) 성공 응답은 **200 + `data: null`**. 204는 쓰지 않는다 — 바디가 없으면 공통 포맷이 깨진다.
- 모든 컨트롤러는 `ResponseEntity<ApiResponse<T>>`를 반환한다.

**예외**: `GET /api/auth/kakao/authorize`와 `/callback`은 이 포맷을 따르지 않는다. 항상 302이며,
실패도 예외를 던지지 않고 `{FE}/oauth/complete?error={code}`로 리다이렉트한다.
브라우저 네비게이션이라 JSON이 사용자 화면에 노출되면 안 되기 때문이다 —
[ADR 0002](../decisions/0002-backend-owns-redirect-uri.md).

## `ErrorResponse`

`global/response/ErrorResponse.java`

```java
public record ErrorResponse(String code, String message, List<FieldError> errors) {
    public record FieldError(String field, String message, String rejectedValue) {}
}
```

- `errors`는 **필드 검증 실패 시에만** 채운다(`MethodArgumentNotValidException`,
  `ConstraintViolationException`). 그 외에는 `null`.
- `rejectedValue`는 `Object`가 아니라 **`String`**이다. `GlobalExceptionHandler`가 `String.valueOf()`로
  변환한 뒤 **200자를 넘으면 자른다** — 내부 객체가 통째로 응답에 노출되는 것을 막는다.
- `traceId` 등 추가 필드는 두지 않는다.

## `PageResponse<T>`

`global/response/PageResponse.java` — `data` 자리에 그대로 삽입한다.

`content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `hasNext`, `hasPrevious`
+ `PageResponse.of(Page<T>)` 팩토리.

목록 조회 API를 만들 때 페이지네이션 구조를 새로 설계하지 말고 `PageResponse.of()`를 재사용한다.
Spring Data `Pageable`을 컨트롤러 파라미터로 바인딩한다(`page`, `size`, `sort`).
기본값과 최대 `size` 제한은 도메인별로 정한다.

## `GlobalExceptionHandler` — 처리 대상 7종

`global/exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`)

| 예외 | 사용 `ErrorCode` | 비고 |
|---|---|---|
| `BusinessException` | `ex.getErrorCode()` | `log.warn` |
| `MethodArgumentNotValidException` | `CommonErrorCode.INVALID_INPUT_VALUE` | `@Valid` 바디 검증. `errors` 조립 |
| `ConstraintViolationException` | `CommonErrorCode.INVALID_INPUT_VALUE` | `@PathVariable`/`@RequestParam` 제약 위반 |
| `DataIntegrityViolationException` | `CommonErrorCode.RESOURCE_CONFLICT` | DB 유니크 제약 위반 |
| `HttpRequestMethodNotSupportedException` | `CommonErrorCode.METHOD_NOT_ALLOWED` | |
| `NoResourceFoundException` | `CommonErrorCode.RESOURCE_NOT_FOUND` | |
| `Exception` (fallback) | `CommonErrorCode.INTERNAL_SERVER_ERROR` | **`log.error` 필수** |

이 외 예외(`HttpMediaTypeNotSupportedException`, `MissingServletRequestParameterException` 등)는
**실제로 필요해질 때 이 표에 추가한다.** 미리 늘리지 않는다.

⚠️ **401/403은 이 핸들러를 타지 않는다.** 필터 단계에서 발생하므로 `JwtAuthenticationEntryPoint`가
`ObjectMapper`로 같은 포맷의 JSON을 직접 쓴다 —
[ADR 0007](../decisions/0007-single-entrypoint-for-401-403.md).

## 지킬 것

- `data`/`error` 동시 존재 없음
- 모든 컨트롤러가 `ResponseEntity<ApiResponse<T>>` 반환 (302 반환하는 OAuth 2개 제외)
- 201 응답에 `Location` 헤더 포함
- 에러 응답의 HTTP 상태 == `ErrorCode.getHttpStatus()` (하드코딩 금지)
- `rejectedValue` 200자 제한 적용
- `global`이 `domain.*`를 import하지 않음

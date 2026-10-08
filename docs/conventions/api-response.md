# 응답 포맷

모든 API 응답이 지키는 구조와, 예외가 그 구조로 변환되는 경로를 정의한다.
에러 코드 값 자체는 [error-codes.md](error-codes.md)가 소유한다.

## `ApiResponse<T>`

`global/response/ApiResponse.java`

```java
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public ApiResponse {                                    // 불변식을 생성자가 강제한다
        if (success && error != null)  throw new IllegalArgumentException("...");
        if (!success && error == null) throw new IllegalArgumentException("...");
        if (!success && data != null)  throw new IllegalArgumentException("...");
    }

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

### 불변식 — 생성자가 강제한다

`success`는 사실상 **파생값**이다: `success == (error == null)`. 아래는 규율이 아니라 기계가 막는다.

| success | data | error | |
|---|---|---|---|
| `true` | 값 | `null` | 일반 성공 |
| `true` | `null` | `null` | **유효** — 데이터 없는 성공(로그아웃·탈퇴) |
| `false` | `null` | 값 | 일반 실패 |
| 나머지 5조합 | | | `IllegalArgumentException` |

- `data`와 `error`는 **배타적**이다. 동시에 값을 가질 수 없다.
- ⚠️ **`(true, null, null)`을 막으면 안 된다.** "정확히 하나만 non-null"로 조이는 순간
  `POST /api/auth/logout`과 `DELETE /api/users/me`가 500으로 깨진다.
- `ApiResponse.error(null)`도 거부된다 — `error` 없는 실패 응답은 클라이언트가 `error.code`를
  읽을 수 없어 성립하지 않는다.
- 위반은 `IllegalArgumentException`이다. 도메인 실패가 아니라 **프로그래밍 오류**이므로
  `BusinessException`으로 감싸지 않고 fallback(`C003`, 500)으로 떨어진다.
- **왜 생성자에서 막는가**: record의 canonical constructor는 record보다 접근성을 낮출 수 없다.
  `public record`면 생성자도 public이 강제되어 `private`으로 숨겨 정적 팩터리 사용을 강요할 수 없다.
  compact constructor가 계약을 코드로 지키는 유일한 수단이다.
- 위 8개 조합은 `ApiResponseTest`가 고정한다.

### 그 밖의 규칙

- **인자 없는 `success()` 정적 팩토리는 만들지 않는다.** record 컴포넌트 `success`(boolean)의 자동 생성
  접근자 `success()`와 시그니처가 충돌해 컴파일 에러가 난다(static/instance 무관). 데이터 없는 성공 응답은
  `ApiResponse.<Void>success(null)`로 반환한다.
- 삭제(DELETE) 성공 응답은 **200 + `data: null`**. 204는 쓰지 않는다 — 바디가 없으면 공통 포맷이 깨진다.

### 컨트롤러 반환 타입

**`ResponseEntity`는 헤더나 상태 코드를 직접 정해야 할 때만 쓴다.** 그 외에는 `ApiResponse<T>`를
그대로 반환한다 — Spring이 200으로 내보낸다.

| 필요한 것 | 반환 타입 |
|---|---|
| 200 + 바디만 | `ApiResponse<T>` |
| 헤더(`Set-Cookie` 등) 또는 200 이외의 상태 | `ResponseEntity<ApiResponse<T>>` |
| 302 리다이렉트 (바디 없음) | `ResponseEntity<Void>` |

판단 여지를 두지 않기 위한 기계적 규칙이다. 덕분에 **시그니처만 봐도 그 응답이 헤더를 건드리는지**
알 수 있다 — `POST /api/auth/refresh`가 `ApiResponse<TokenResponse>`인 것은 rotation을 하지 않아
**재발급 응답에 `Set-Cookie`가 없다**는 뜻이다
([ADR 0004](../decisions/auth/0004-no-refresh-token-rotation.md)). 전부 `ResponseEntity`로 감싸면
이 신호가 사라진다.

**예외 처리와는 무관하다.** 예외가 나면 컨트롤러는 반환값을 만들지 못하고 종료하며, 응답은
`GlobalExceptionHandler`가 만든다. 두 반환 타입이 한 응답에서 만나는 일이 없으므로,
plain 반환이어도 실패 시 상태 코드는 정상적으로 내려간다
(`refresh`의 A007 → 401을 `AuthControllerTest`가 고정하고 있다).

`@ExceptionHandler`가 `ResponseEntity`를 쓰는 것도 같은 규칙이다 — `errorCode.getHttpStatus()`가
401·403·500으로 **런타임에 달라지기** 때문이다. plain 반환은 항상 200이 되고, `@ResponseStatus`는
컴파일 타임 고정값이라 쓸 수 없다.

**예외**: `GET /api/auth/kakao/authorize`와 `/callback`은 이 포맷을 따르지 않는다. 항상 302이며,
실패도 예외를 던지지 않고 `{FE}/oauth/complete?error={code}`로 리다이렉트한다.
브라우저 네비게이션이라 JSON이 사용자 화면에 노출되면 안 되기 때문이다 —
[ADR 0002](../decisions/auth/0002-backend-owns-redirect-uri.md).

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
| `HttpMessageNotReadableException` | `CommonErrorCode.INVALID_INPUT_VALUE` | 본문을 객체로 못 바꿈(깨진 JSON·정수 범위 초과·타입 불일치). `@Valid`보다 앞이라 위 둘이 잡지 못한다. 필드를 알면 `errors` |
| `DataIntegrityViolationException` | `CommonErrorCode.RESOURCE_CONFLICT` | DB 유니크 제약 위반 |
| `HttpRequestMethodNotSupportedException` | `CommonErrorCode.METHOD_NOT_ALLOWED` | |
| `NoResourceFoundException` | `CommonErrorCode.RESOURCE_NOT_FOUND` | |
| `Exception` (fallback) | `CommonErrorCode.INTERNAL_SERVER_ERROR` | **`log.error` 필수** |

이 외 예외(`HttpMediaTypeNotSupportedException`, `MissingServletRequestParameterException` 등)는
**실제로 필요해질 때 이 표에 추가한다.** 미리 늘리지 않는다.

⚠️ **401/403은 이 핸들러를 타지 않는다.** 필터 단계에서 발생하므로 인증 실패는
`JwtAuthenticationEntryPoint`가, 인가 실패(403)는 `JwtAccessDeniedHandler`가
`ObjectMapper`로 같은 포맷의 JSON을 직접 쓴다 —
[ADR 0010](../decisions/platform/0010-access-denied-handler-for-authorization.md).

## 지킬 것

사람이 확인해야 하는 것만 남긴다. `data`/`error` 배타성과 `success` 일관성은
`ApiResponse` 생성자가 막으므로 여기 두지 않는다.

- 컨트롤러 반환 타입이 위 표와 맞는지 — 헤더·상태 제어가 없는데 `ResponseEntity`로 감싸지 않았는지
- 201 응답에 `Location` 헤더 포함. **상대 경로**(`/api/products/1`)로 쓴다 —
  받는 쪽이 요청 URI를 기준으로 해석하므로(RFC 3986 §5) 앱이 자기 공개 주소를 알 필요가 없다.
  절대 URL로 조립하면 리버스 프록시 뒤에서 내부 주소가 나가고, 그걸 막으려면
  `X-Forwarded-*` 처리나 프록시의 헤더 재작성이 따라붙는다 ([deployment.md](../deployment.md))
- 에러 응답의 HTTP 상태 == `ErrorCode.getHttpStatus()` (하드코딩 금지)
- `rejectedValue` 200자 제한 적용
- `global/response`·`global/exception`·`global/util`이 `domain.*`를 import하지 않음
  (Spring Security 배선인 `global/security`·`global/config/SecurityConfig`만 예외 —
  [architecture.md](../architecture.md)의 의존 규칙). 확인:
  ```bash
  grep -rn "^import com.toyproject.backend.domain" src/main/java/com/toyproject/backend/global/ \
    | grep -v "global/security/\|global/config/SecurityConfig.java"
  ```

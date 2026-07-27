# 공통 응답 포맷 — 구현 스펙

> 상태: 확정 설계, 미구현. 기존 `global/response/ApiResponse.java`(`{code, message, data}`)를 아래 스키마로 **교체**한다. `PageResponse`는 교체 대상 아님(3절).

## 1. `ApiResponse<T>`

```java
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(true, data, null); }
    public static ApiResponse<Void> error(ErrorResponse error) { return new ApiResponse<>(false, null, error); }
}
```

- `data`/`error`는 항상 배타적 (동시에 값 존재 금지).
- 인자 없는 `success()` 정적 팩토리는 만들지 않는다 — record 컴포넌트 `success`(boolean)의 자동 생성 접근자 `success()`와 이름이 충돌해 컴파일 에러가 난다(static/instance 무관하게 동일 시그니처 금지). 데이터 없는 성공 응답은 `ApiResponse.<Void>success(null)`로 반환한다.
- 삭제(DELETE) 성공 응답: 200 + `ApiResponse.<Void>success(null)` (`data: null`). 204 방식은 채택하지 않음.

## 2. `ErrorResponse`

```java
public record ErrorResponse(String code, String message, List<FieldError> errors) {
    public record FieldError(String field, String message, String rejectedValue) {}
}
```

- `rejectedValue`는 `Object`가 아니라 `String`. `GlobalExceptionHandler`에서 `String.valueOf(value)` 변환 후 200자 초과 시 자르는 `safeToString()` 유틸을 거쳐야 함(내부 객체 통째 노출 방지).
- `errors`는 필드 검증 실패(`MethodArgumentNotValidException`, `ConstraintViolationException`) 시에만 채움, 그 외 `null`.
- `traceId` 등 추가 필드 없음.

## 3. `PageResponse<T>` — 변경 없음

기존 `global/response/PageResponse.java` 그대로 재사용 (`content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `hasNext`, `hasPrevious`). `data` 자리에 그대로 삽입.

## 4. `ErrorCode` — 인터페이스 + 도메인별 분리

기존 `global/exception/ErrorCode.java`(단일 enum)를 아래처럼 쪼갠다. **코드값(`P001` 등)은 절대 변경하지 않고 그대로 이관**.

```java
public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
```

| 파일 | 위치 | 이관 대상 (기존 코드값 그대로) |
|---|---|---|
| `CommonErrorCode` | `global.exception` | `C001` INVALID_INPUT_VALUE(400), `C002` RESOURCE_NOT_FOUND(404), `C003` INTERNAL_SERVER_ERROR(500) + 신규 `C004` METHOD_NOT_ALLOWED(405), `C005` RESOURCE_CONFLICT(409) |
| `UserErrorCode` | `domain.user.exception` | `U001`~`U006` (DUPLICATE_EMAIL, INVALID_CREDENTIALS, UNAUTHORIZED, FORBIDDEN, INVALID_TOKEN, EXPIRED_TOKEN) |
| `ProductErrorCode` | `domain.product.exception` | `P001` PRODUCT_NOT_FOUND, `P002` OUT_OF_STOCK |
| `CategoryErrorCode` | `domain.category.exception` | `CA001` CATEGORY_NOT_FOUND, `CA002` CATEGORY_HAS_CHILDREN |
| `CartErrorCode` | `domain.cart.exception` | `CT001` CART_ITEM_NOT_FOUND, `CT002` CART_QUANTITY_EXCEEDED |
| `OrderErrorCode` | `domain.order.exception` | `O001` ORDER_NOT_FOUND, `O002` ORDER_CANCEL_NOT_ALLOWED |
| `PaymentErrorCode` | `domain.payment.exception` | `PAY001` PAYMENT_NOT_FOUND, `PAY002` PAYMENT_ALREADY_EXISTS |
| `ReviewErrorCode` | `domain.review.exception` | `R001` REVIEW_NOT_FOUND, `R002` REVIEW_ALREADY_EXISTS, `R003` REVIEW_NOT_ALLOWED |

명명 규칙: `{도메인 접두사}{3자리 일련번호}`. 유지, 확장 안 함.

`BusinessException`은 필드 타입을 `ErrorCode`(인터페이스)로 유지 — 이미 그렇게 구현되어 있음, 변경 불필요.

## 5. `GlobalExceptionHandler` — 처리 대상 (총 7종)

기존 3종(`BusinessException`, `MethodArgumentNotValidException`, `Exception`)에 아래 4종 추가.

| 예외 | 사용 `ErrorCode` | 비고 |
|---|---|---|
| `BusinessException` | `ex.getErrorCode()` | 기존 유지 |
| `MethodArgumentNotValidException` | `CommonErrorCode.INVALID_INPUT_VALUE` | 기존 유지, `errors` 필드 조립 추가 |
| `ConstraintViolationException` | `CommonErrorCode.INVALID_INPUT_VALUE` | 신규 — `@PathVariable`/`@RequestParam` 제약 위반 |
| `DataIntegrityViolationException` | `CommonErrorCode.RESOURCE_CONFLICT` | 신규 — DB 유니크 제약 위반(동시 요청) |
| `HttpRequestMethodNotSupportedException` | `CommonErrorCode.METHOD_NOT_ALLOWED` | 신규 |
| `NoResourceFoundException` | `CommonErrorCode.RESOURCE_NOT_FOUND` | 신규 |
| `Exception` (fallback) | `CommonErrorCode.INTERNAL_SERVER_ERROR` | 기존 유지, `log.error` 필수 |

`BusinessException` 핸들러는 `log.warn`, fallback은 `log.error` 유지.

이 외 예외(`HttpMediaTypeNotSupportedException`, `MissingServletRequestParameterException` 등)는 지금 추가하지 않음 — 실제로 필요해질 때 이 표에 추가.

## 6. 패키지 구조

```
global
 ├── response/ {ApiResponse, PageResponse, ErrorResponse}
 └── exception/ {ErrorCode, CommonErrorCode, BusinessException, GlobalExceptionHandler}
domain/{user,product,category,cart,order,payment,review}/exception/XxxErrorCode.java
```

의존 방향: `domain → global`만 허용. `global`이 특정 도메인을 import하면 안 됨.

## 7. 미결 (지금 구현 안 함)

- Auth 관련 `ErrorCode`(`U003`~`U006`) 사용은 Phase 2(User 도메인) 구현 시점에 `JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler`와 함께 연결.
- `traceId`, MSA 대응 필드는 스코프 아님.

## 8. 체크리스트

- [ ] `data`/`error` 동시 존재 없음
- [ ] 모든 컨트롤러가 `ResponseEntity<ApiResponse<T>>` 반환
- [ ] 201 응답에 `Location` 헤더 포함
- [ ] 에러 응답 HTTP 상태 == `ErrorCode.getHttpStatus()`
- [ ] `rejectedValue` 200자 제한 적용
- [ ] `global`이 `domain.*`를 import하지 않음

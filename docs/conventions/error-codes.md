# 에러 코드 레지스트리

여기 적힌 **코드 값은 대외 계약이다.** FE가 분기에 쓰므로 한 번 나간 값은 바꾸지 않는다.
사람이 읽는 `message`는 바꿔도 되지만 `code`는 고정이다.

응답을 감싸는 구조는 [api-response.md](api-response.md)를 참고한다.

## 구조

`ErrorCode`는 인터페이스이고, 도메인별 enum이 구현한다.

```java
public interface ErrorCode {          // global/exception/ErrorCode.java
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
```

`BusinessException`은 필드 타입을 이 **인터페이스**로 들고 있어, 새 도메인이 생겨도 global을 고치지 않는다.
의존 방향은 `domain → global` 단방향이다 — `global/exception`은 어떤 도메인의 `*ErrorCode`도 알지 못한다.

> **예외**: `global/security`와 `global/config/SecurityConfig`는 `AuthErrorCode`를 직접 참조한다.
> 필터 단계라 `GlobalExceptionHandler`를 타지 않아 Spring Security 배선이 직접 코드를 골라야 하기 때문이다.
> 자세한 범위는 [architecture.md](../architecture.md)의 의존 규칙을 참고한다.

명명 규칙: **`{도메인 접두사}{3자리 일련번호}`**. 새 실패 케이스는 해당 도메인 enum의 다음 번호로 잇는다.

## `CommonErrorCode` — `global.exception`

| 코드 | 상수 | HTTP | 용도 |
|---|---|---|---|
| C001 | `INVALID_INPUT_VALUE` | 400 | 유효하지 않은 입력값 |
| C002 | `RESOURCE_NOT_FOUND` | 404 | 요청한 리소스 없음 |
| C003 | `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |
| C004 | `METHOD_NOT_ALLOWED` | 405 | 허용되지 않은 HTTP 메서드 |
| C005 | `RESOURCE_CONFLICT` | 409 | 현재 리소스 상태와 충돌 |

## `AuthErrorCode` — `domain.auth.exception`

| 코드 | 상수 | HTTP | 용도 |
|---|---|---|---|
| A001 | `UNAUTHORIZED` | 401 | 인증 필요 (토큰 없음·기타) |
| A002 | `FORBIDDEN` | 403 | 접근 권한 없음 |
| A003 | `INVALID_TOKEN` | 401 | JWT 서명 위조·형식 오류 |
| A004 | `EXPIRED_TOKEN` | 401 | JWT 만료 |
| A005 | `KAKAO_AUTH_FAILED` | 400 | 카카오 인증 실패 (4xx) |
| A006 | `KAKAO_API_ERROR` | 502 | 카카오 서버 오류 (5xx) |
| A007 | `REFRESH_TOKEN_NOT_FOUND` | 401 | refresh token 미존재 **또는** 만료 |
| A008 | `WITHDRAWN_USER` | 403 | 탈퇴한 회원 |
| A009 | `INVALID_OAUTH_STATE` | 400 | `state` 불일치·누락 |

- A003/A004는 **JWT 자체**의 서명·만료 오류에 쓴다.
- A007은 미존재와 만료를 **의도적으로 뭉갠다** — 토큰의 존재 여부를 흘리지 않기 위한 정보 은닉이다.
- **A008이 auth에 있는 이유**: 회원 상태에서 파생되지만 실제 발생 지점이 `CustomUserDetailsService`·
  `JwtAuthenticationFilter`이고 "인증 거절 사유"로 소비된다.
- **A005·A006·A009는 JSON으로 나가지 않는다.** 콜백에서 발생하므로 FE 복귀 URL의 `?error=` 값으로
  전달된다(`{FE}/oauth/complete?error=A009`) — [ADR 0002](../decisions/0002-backend-owns-redirect-uri.md).
- `INVALID_CREDENTIALS`(옛 U002)는 카카오 전용 설계에서 도달 경로가 없어 **삭제했다**.
  LOCAL 로그인을 도입하면 `AuthErrorCode`에 다시 추가한다 —
  [ADR 0001](../decisions/0001-kakao-only-oauth.md).

## 도메인별

| 코드 | 상수 | HTTP | 위치 |
|---|---|---|---|
| U001 | `DUPLICATE_EMAIL` | 409 | `domain.user.exception.UserErrorCode` |
| CA001 | `CATEGORY_NOT_FOUND` | 404 | `domain.category.exception.CategoryErrorCode` |
| CA002 | `CATEGORY_HAS_CHILDREN` | 409 | 〃 |
| CA003 | `CATEGORY_DEPTH_EXCEEDED` | 400 | 〃 |
| P001 | `PRODUCT_NOT_FOUND` | 404 | `domain.product.exception.ProductErrorCode` |
| P002 | `OUT_OF_STOCK` | 409 | 〃 |
| CT001 | `CART_ITEM_NOT_FOUND` | 404 | `domain.cart.exception.CartErrorCode` |
| CT002 | `CART_QUANTITY_EXCEEDED` | 409 | 〃 |
| O001 | `ORDER_NOT_FOUND` | 404 | `domain.order.exception.OrderErrorCode` |
| O002 | `ORDER_CANCEL_NOT_ALLOWED` | 409 | 〃 |
| PAY001 | `PAYMENT_NOT_FOUND` | 404 | `domain.payment.exception.PaymentErrorCode` |
| PAY002 | `PAYMENT_ALREADY_EXISTS` | 409 | 〃 |
| R001 | `REVIEW_NOT_FOUND` | 404 | `domain.review.exception.ReviewErrorCode` |
| R002 | `REVIEW_ALREADY_EXISTS` | 409 | 〃 |
| R003 | `REVIEW_NOT_ALLOWED` | 403 | 〃 |

Category·Product·Cart·Order·Payment·Review의 코드는 **해당 도메인 구현 전에 선반영**된 것이다.
enum은 있지만 이를 던지는 코드는 아직 없다.

## 도메인별 구체 예외 클래스

`BusinessException`을 상속하는 구체 클래스(`ProductNotFoundException` 등)는 **아직 만들지 않았다.**
필요해지는 시점에 해당 도메인의 `exception` 패키지에 추가한다. 지금은 `BusinessException`에
`ErrorCode`를 담아 던지는 것으로 충분하다.

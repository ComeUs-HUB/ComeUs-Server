# 결정 기록 (ADR)

이 디렉터리는 **왜 그렇게 정했는가**를 기록한다. *무엇이 구현돼 있는가*는 [domains/](../domains/)와
[architecture.md](../architecture.md)가, *무엇이 끝났는가*는 [roadmap.md](../roadmap.md)가 소유한다.

## 규칙

- **한 번 쓴 ADR은 고치지 않는다.** 오타 수정 외에는 본문을 바꾸지 않는다.
- 결정이 뒤집히면 기존 파일의 `상태:`를 `대체됨 → ADR NNNN`으로만 바꾸고, **새 ADR을 추가**한다.
- 뒤집히지 않고 **일부만 바뀌거나 미결 항목이 해소되면** `상태:`를 `채택 (NNNN이 보강)`으로 바꾸고
  역시 새 ADR을 추가한다. 본문은 그대로 둔다 — 그때의 판단이 기록이고, 후속은 상태 줄이 가리킨다.
- 번호는 이어붙이기만 한다. 재사용·재정렬하지 않는다. **번호는 디렉터리와 무관하게 전역으로 이어진다.**
- 다른 문서는 결정 근거를 복사하지 않고 여기로 링크한다.
- 파일은 [domains/](../domains/)와 같은 이름의 디렉터리에 둔다 — `auth/`, `catalog/`, `purchase/`, `review/`.
  도메인이 아니라 `global/` 소관인 결정(필터체인, 스키마 관리 등)은 `platform/`에 둔다.
  두 도메인에 걸치면 **결정을 촉발한 쪽**에 두고 다른 쪽에서 링크한다(예: [0012](catalog/0012-image-urls-in-separate-table.md)).

일반적인 모범 사례와 **다르게** 정한 것, 되돌리는 비용이 큰 것, 리뷰에서 이견이 있었던 것만 남긴다.
"당연한 선택"은 ADR로 만들지 않는다.

## 목록

### auth — 회원 · 인증

계약은 [domains/auth.md](../domains/auth.md)가 소유한다.

| # | 결정 | 상태 |
|---|---|---|
| [0001](auth/0001-kakao-only-oauth.md) | 카카오 OAuth 전용 로그인 — 이메일+비밀번호 없음 | 채택 |
| [0002](auth/0002-backend-owns-redirect-uri.md) | `redirect_uri`는 백엔드가 소유한다 | 채택 |
| [0003](auth/0003-no-pkce-yet.md) | PKCE 미적용 | 보류 |
| [0004](auth/0004-no-refresh-token-rotation.md) | Refresh Token Rotation 미적용 | 보류 |
| [0005](auth/0005-opaque-refresh-token-hashed-in-db.md) | Refresh Token은 불투명 랜덤 문자열, DB엔 해시만 | 채택 |
| [0006](auth/0006-cookie-samesite-and-path.md) | 쿠키 2종의 `SameSite`·`Path`를 다르게 둔다 | 채택 |
| [0009](auth/0009-signup-race-retry-at-login-boundary.md) | 가입 경합을 로그인 경계에서 1회 재시도로 흡수 | 채택 |

### catalog — 카테고리 · 상품

계약은 [domains/catalog/](../domains/catalog/README.md)가 소유한다.

| # | 결정 | 상태 |
|---|---|---|
| [0011](catalog/0011-jpa-specification-for-product-filter.md) | 상품 목록의 동적 필터는 JPA Specification으로 시작 | 채택 |
| [0012](catalog/0012-image-urls-in-separate-table.md) | 상품·리뷰 이미지는 별도 테이블에 URL만 저장 | 채택 |
| [0013](catalog/0013-hide-soft-deleted-products-everywhere.md) | soft delete된 상품은 ADMIN에게도 숨긴다 | 채택 |
| [0014](catalog/0014-category-reads-product-repository.md) | 카테고리 삭제 차단은 `ProductRepository`를 직접 읽는다 | 채택 |
| [0015](catalog/0015-no-stock-quantity-in-public-response.md) | 공개 상품 응답은 재고 수량을 담지 않는다 | 채택 |

### purchase — 장바구니 · 주문 · 결제

계약은 [domains/purchase/](../domains/purchase/README.md)가 소유한다.

| # | 결정 | 상태 |
|---|---|---|
| [0016](purchase/0016-stock-reserved-at-order-not-cart.md) | 재고 점유는 주문 생성에서 시작하고 장바구니는 점유하지 않는다 | 채택 (0025가 보강) |
| [0017](purchase/0017-order-copies-cart-and-cuts-the-link.md) | 주문은 장바구니를 값으로 복사하고 연결을 끊는다 | 채택 |
| [0018](purchase/0018-no-product-options-yet.md) | 상품 옵션(사이즈·색상)을 초기 범위 밖에 둔다 | 채택 |
| [0019](purchase/0019-cart-owns-item-deletion.md) | 장바구니 항목 삭제는 애그리거트가 소유한다 | 채택 |
| [0020](purchase/0020-cart-writes-use-read-committed.md) | 장바구니 쓰기는 READ COMMITTED에서 루트 락을 잡는다 | 채택 (0025가 보강) |
| [0024](purchase/0024-order-idempotency-key-in-header.md) | 주문 생성의 중복은 헤더 멱등키와 복합 유니크로 막는다 | 채택 (0026·0027이 보강) |
| [0025](purchase/0025-pessimistic-lock-as-stock-concurrency-baseline.md) | 재고 동시성은 비관적 락을 기준선으로 시작하고 전환은 측정치로 판단한다 | 채택 |
| [0026](purchase/0026-payment-confirm-outside-transaction.md) | 결제 승인은 트랜잭션 밖에서 호출하고 주문 상태 선점으로 한 번만 진입시킨다 | 채택 |
| [0027](purchase/0027-idempotency-uk-violation-responds-conflict.md) | 멱등키 uk 위반은 기존 주문을 찾지 않고 409로 응답한다 | 채택 |

### platform — 전역 기반

계약은 [architecture.md](../architecture.md)가 소유한다.

| # | 결정 | 상태 |
|---|---|---|
| [0007](platform/0007-single-entrypoint-for-401-403.md) | 401·403을 `JwtAuthenticationEntryPoint` 하나가 전담 | 대체됨 → 0010 |
| [0008](platform/0008-flyway-owns-schema.md) | 스키마 소유자는 Flyway (`ddl-auto: validate`) | 채택 |
| [0010](platform/0010-access-denied-handler-for-authorization.md) | 인가 실패(403)는 `JwtAccessDeniedHandler`가 맡는다 | 채택 |
| [0021](platform/0021-user-repository-in-security.md) | `global/security`는 `UserRepository`를 직접 주입한다 | 채택 |
| [0022](platform/0022-config-validation-split.md) | 설정 누락은 서명 키만 기동에서 막는다 | 채택 (0023이 보강) |
| [0023](platform/0023-fail-fast-when-no-safe-default.md) | 안전한 기본값을 정할 수 없는 설정은 기동에서 막는다 | 채택 |

`review/`는 Phase 5에서 첫 ADR이 생길 때 만든다.

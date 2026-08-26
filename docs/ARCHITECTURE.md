# ARCHITECTURE — 의류 판매 웹사이트 백엔드 API

## 1. 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | Clothing Store Backend API |
| 기술 스택 | Spring Boot 3.5.0 · Java 21 · JPA · MySQL · Gradle (Kotlin DSL) |
| 목표 | 의류 판매 웹사이트의 REST API 서버 구축 |

---

## 2. 도메인 목록

| 도메인 | 설명 |
|--------|------|
| User | 회원 프로필 관리 · 회원 생명주기(가입 · 탈퇴) |
| Auth | 카카오 소셜 로그인 · JWT/Refresh Token 발급 · 검증 |
| Product | 상품 등록 · 조회 · 수정 · 삭제 |
| Category | 상품 카테고리 관리 |
| Cart | 장바구니 담기 · 조회 · 수정 · 삭제 |
| Order | 주문 생성 · 조회 · 취소 |
| Payment | 결제 처리 |
| Review | 상품 리뷰 작성 · 조회 |

---

## 3. 도메인별 기능 요구사항

### 3.1 User / Auth (회원 · 인증)

두 도메인으로 분리되어 있다. 패키지 경계와 의존 규칙은 §5 참고.

**엔티티 주요 필드**
- `User`(user) — id, email(nullable), name, phone, address, birthDate, role(USER/ADMIN), provider(KAKAO), providerId, status(ACTIVE/WITHDRAWN), withdrawnAt, createdAt, updatedAt
- `RefreshToken`(auth) — id, user(`@ManyToOne`), tokenHash(SHA-256 hex, unique), expiresAt, createdAt

**API 목록**

| Method | URI | 설명 | 권한 | 도메인 |
|--------|-----|------|------|--------|
| GET | /api/auth/kakao/authorize | 카카오 로그인 시작 (302 → 카카오) | 비회원 | auth |
| GET | /api/auth/kakao/callback | 카카오 리다이렉트 착지점 (302 → FE) | 비회원 | auth |
| POST | /api/auth/refresh | Access Token 재발급 | 비회원 | auth |
| POST | /api/auth/logout | 로그아웃 | 비회원 (쿠키가 자격증명) | auth |
| GET | /api/users/me | 내 정보 조회 | 회원 | user |
| PUT | /api/users/me | 내 정보 수정 | 회원 | user |
| DELETE | /api/users/me | 회원 탈퇴 | 회원 | user (+auth 호출) |

**비즈니스 규칙**
- **카카오 OAuth 전용 로그인** — 이메일+비밀번호 가입/로그인은 범위 밖(향후 확장)
- **`redirect_uri`는 백엔드가 소유** — 카카오는 BE 콜백으로 리다이렉트하고, 인가코드는 FE에 도달하지 않는다 (Authorization Code Grant의 confidential client 모델). CSRF 방어로 `state` 필수
- `(provider, providerId)` 복합 유니크로 동일 카카오 계정 중복 가입 방지. `email`은 카카오 동의 거부 시 null 가능
- JWT (Access Token 30분 + Refresh Token 7일) 방식 인증
- Refresh Token은 DB `refresh_tokens` 테이블에 **SHA-256 해시로** 저장, 값 자체는 JWT가 아닌 불투명 랜덤 문자열
- 탈퇴는 소프트 삭제(status=WITHDRAWN). 동일 카카오 계정 재로그인 시 자동 재활성화
- ADMIN 역할은 상품·카테고리·주문 관리 권한 보유

---

### 3.2 Category (카테고리)

**엔티티 주요 필드**
- id, name, parentCategory(self-join, nullable), displayOrder, createdAt

**API 목록**

| Method | URI | 설명 | 권한 |
|--------|-----|------|------|
| GET | /api/categories | 전체 카테고리 트리 조회 | 비회원 |
| POST | /api/categories | 카테고리 생성 | ADMIN |
| PUT | /api/categories/{id} | 카테고리 수정 | ADMIN |
| DELETE | /api/categories/{id} | 카테고리 삭제 | ADMIN |

**비즈니스 규칙**
- 2depth 구조 (대분류 > 소분류) 지원, self-join으로 구현
- 하위 카테고리가 있는 카테고리는 삭제 불가 (`CATEGORY_HAS_CHILDREN`)

---

### 3.3 Product (상품)

**엔티티 주요 필드**
- id, name, description, price, stockQuantity, category, imageUrls, status(ON_SALE/SOLD_OUT/HIDDEN), createdAt, updatedAt

**API 목록**

| Method | URI | 설명 | 권한 |
|--------|-----|------|------|
| GET | /api/products | 상품 목록 조회 (페이징·필터·정렬) | 비회원 |
| GET | /api/products/{id} | 상품 상세 조회 | 비회원 |
| POST | /api/products | 상품 등록 | ADMIN |
| PUT | /api/products/{id} | 상품 수정 | ADMIN |
| DELETE | /api/products/{id} | 상품 삭제 | ADMIN |

**비즈니스 규칙**
- 재고 0 → status 자동으로 SOLD_OUT 처리
- 목록 조회: 카테고리·가격 범위·키워드 필터, 최신순·가격순 정렬 (`Pageable`)
- 삭제는 soft delete (status = HIDDEN)

---

### 3.4 Cart (장바구니)

**엔티티 주요 필드**
- id, user, cartItems[ { product, size, color, quantity } ]

**API 목록**

| Method | URI | 설명 | 권한 |
|--------|-----|------|------|
| GET | /api/cart | 내 장바구니 조회 | 회원 |
| POST | /api/cart/items | 상품 담기 | 회원 |
| PUT | /api/cart/items/{itemId} | 수량 변경 | 회원 |
| DELETE | /api/cart/items/{itemId} | 항목 삭제 | 회원 |
| DELETE | /api/cart | 장바구니 전체 비우기 | 회원 |

**비즈니스 규칙**
- 동일 상품(+옵션) 재담기 시 수량 합산
- 재고 초과 수량 담기 불가 (`CART_QUANTITY_EXCEEDED`)

---

### 3.5 Order (주문)

**엔티티 주요 필드**
- id, user, orderItems[ { product, size, color, quantity, priceAtOrder } ], totalAmount, status(PENDING/PAID/SHIPPING/DELIVERED/CANCELLED), shippingAddress, createdAt

**API 목록**

| Method | URI | 설명 | 권한 |
|--------|-----|------|------|
| POST | /api/orders | 주문 생성 | 회원 |
| GET | /api/orders | 내 주문 목록 | 회원 |
| GET | /api/orders/{id} | 주문 상세 | 회원 |
| POST | /api/orders/{id}/cancel | 주문 취소 | 회원 |
| GET | /api/admin/orders | 전체 주문 목록 | ADMIN |
| PUT | /api/admin/orders/{id}/status | 주문 상태 변경 | ADMIN |

**비즈니스 규칙**
- 주문 생성 시 재고 차감 (비관적 락 `PESSIMISTIC_WRITE` 적용)
- PAID 이후 취소 시 재고 복구
- SHIPPING 이후 회원이 직접 취소 불가 (`ORDER_CANCEL_NOT_ALLOWED`)
- 주문 시점의 가격을 `priceAtOrder`에 스냅샷 저장

---

### 3.6 Payment (결제)

**엔티티 주요 필드**
- id, order, amount, method(CARD/TRANSFER), status(SUCCESS/FAILED/REFUNDED), paidAt

**API 목록**

| Method | URI | 설명 | 권한 |
|--------|-----|------|------|
| POST | /api/payments | 결제 요청 | 회원 |
| GET | /api/payments/{orderId} | 결제 정보 조회 | 회원 |
| POST | /api/payments/{id}/refund | 환불 요청 | 회원 |

**비즈니스 규칙**
- 결제 성공 시 Order status → PAID 전환
- 결제 실패 시 재고 복구
- 1 Order : 1 Payment 제약
- 외부 PG사 연동은 `PaymentGateway` 인터페이스로 추상화 (현재 Mock 처리)

---

### 3.7 Review (리뷰)

**엔티티 주요 필드**
- id, user, product, order, rating(1~5), content, imageUrls, createdAt

**API 목록**

| Method | URI | 설명 | 권한 |
|--------|-----|------|------|
| GET | /api/products/{id}/reviews | 상품 리뷰 목록 | 비회원 |
| POST | /api/products/{id}/reviews | 리뷰 작성 | 회원 |
| PUT | /api/reviews/{id} | 리뷰 수정 | 회원(본인) |
| DELETE | /api/reviews/{id} | 리뷰 삭제 | 회원(본인)/ADMIN |

**비즈니스 규칙**
- DELIVERED 상태 주문 건에 한해 리뷰 작성 가능 (`REVIEW_NOT_ALLOWED`)
- 1 Order : 1 Review (중복 작성 불가, `REVIEW_ALREADY_EXISTS`)
- 평균 평점은 상품 조회 시 집계하여 반환

---

## 4. 공통 기술 설계

### 4.1 인증 / 인가

- Spring Security 6.x `SecurityFilterChain` 방식 (WebSecurityConfigurerAdapter 미사용)
- JWT Access Token (30분) + Refresh Token (7일)
- Access Token은 응답 바디 → FE 메모리 보관, Refresh Token은 `HttpOnly + Secure + SameSite=Strict` 쿠키(`Path=/api/auth`). access token은 URL에 싣지 않는다
- OAuth 인가 왕복용 `oauth_state` 쿠키는 `SameSite=Lax` — 콜백이 cross-site 네비게이션이라 `Strict`면 전송되지 않는다
- Refresh Token Rotation은 **적용하지 않는다** — 재사용 탐지와 함께 도입해야 실익이 있어 둘 다 향후 과제 ([[phase2-auth]] I 항목)
- Refresh Token 저장: DB `refresh_tokens` 테이블에 SHA-256 해시 (추후 Redis 교체 가능)
- ⚠️ `SameSite=Strict`는 FE/BE 동일 상위 도메인 배포를 전제로 한다 ([[phase7-deployment]] X 항목)
- 401 / 403 응답은 `JwtAuthenticationEntryPoint` **하나가 전담** — `AccessDeniedHandler`는 두지 않는다 ([[phase2-auth]] H 항목)

### 4.2 응답 포맷

모든 API 응답은 아래 포맷을 준수한다.

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- 성공: `ApiResponse.success(data)`
- 실패: `ApiResponse.error(status, message)`
- 페이지네이션: `data` 필드에 `PageResponse<T>` 삽입

### 4.3 예외 처리

계층 구조:
```
RuntimeException
└── BusinessException (ErrorCode 보유)
    └── 도메인별 커스텀 예외 (e.g. ProductNotFoundException)
```

- `ErrorCode` enum: status(HTTP 코드) · code(도메인 코드) · message(한국어 메시지)
- `GlobalExceptionHandler`에서 `BusinessException`, `MethodArgumentNotValidException`, `Exception` 일괄 처리

### 4.4 페이지네이션

- Spring Data `Pageable` 파라미터 바인딩 (`page`, `size`, `sort`)
- 응답: `PageResponse<T>` — `content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `hasNext`, `hasPrevious`

### 4.5 유효성 검증

- `spring-boot-starter-validation` 사용
- 컨트롤러 파라미터에 `@Valid` 적용
- 실패 시 `MethodArgumentNotValidException` → `GlobalExceptionHandler`에서 400 반환

### 4.6 RBAC 권한 매트릭스

| 도메인 | 비회원 | USER | ADMIN |
|--------|--------|------|-------|
| 상품 조회 | ✅ | ✅ | ✅ |
| 상품 CRUD | ❌ | ❌ | ✅ |
| 카테고리 조회 | ✅ | ✅ | ✅ |
| 카테고리 CRUD | ❌ | ❌ | ✅ |
| 장바구니 | ❌ | ✅ | ✅ |
| 주문 (본인) | ❌ | ✅ | ✅ |
| 주문 전체 조회 | ❌ | ❌ | ✅ |
| 결제 | ❌ | ✅ | ✅ |
| 리뷰 조회 | ✅ | ✅ | ✅ |
| 리뷰 작성/수정 | ❌ | ✅ (본인) | ✅ |
| 리뷰 삭제 | ❌ | ✅ (본인) | ✅ |

---

## 5. 패키지 구조

```
com.toyproject.backend
├── domain
│   ├── user                    # 회원 프로필 · 생명주기
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity              # User, Role, UserStatus, Provider
│   │   ├── dto
│   │   └── exception           # UserErrorCode
│   ├── auth                    # 카카오 로그인 · 토큰
│   │   ├── controller
│   │   ├── service
│   │   ├── client              # KakaoOAuthClient (+dto)
│   │   ├── token               # JwtProvider, SecureTokenGenerator
│   │   ├── config              # JwtProperties, KakaoProperties, RestClientConfig
│   │   ├── entity              # RefreshToken
│   │   ├── repository
│   │   ├── dto
│   │   └── exception           # AuthErrorCode
│   ├── product
│   ├── category
│   ├── cart
│   ├── order
│   ├── payment
│   └── review
└── global
    ├── config        # SecurityConfig, AppProperties, JpaConfig
    ├── exception     # GlobalExceptionHandler, BusinessException, ErrorCode
    ├── response      # ApiResponse, PageResponse
    ├── security      # JwtAuthenticationFilter, JwtAuthenticationEntryPoint,
    │                 #   CustomUserDetailsService, UserPrincipal
    └── util          # CookieUtil
```

### user / auth 분리 원칙

| 항목 | 규칙 |
|---|---|
| 의존 방향 | **`auth → user` 단방향.** user는 auth를 참조하지 않는다 |
| 회원 데이터 접근 | auth는 `UserService`의 공개 메서드만 경유. `UserRepository`는 user 패키지 안에 가둔다 |
| 회원 생성 규칙 | `UserService.findOrCreateKakaoUser`가 소유 (재사용/재활성화/신규 생성 판단) |
| 탈퇴 | `UserController`가 `authService.revokeAllTokens` → `userService.withdraw` → `authService.unlinkKakao` 순으로 호출해 순환 의존을 차단 |
| `global/security` | Spring Security 어댑터만 남긴다. 도메인을 참조하는 방향은 허용(프레임워크 결합 지점) |

---

## 6. 현재 구현 상태

### Phase 0 — 환경 설정 ✅

| 항목 | 상태 | 비고 |
|------|------|------|
| Spring Boot 3.5.0 + Java 21 + Gradle (Kotlin DSL) 프로젝트 생성 | ✅ | |
| 패키지 구조 생성 | ✅ | domain/global 전체 디렉터리 |
| 의존성 추가 | ✅ | security, jjwt 0.12.6, spring-security-test 포함 |
| `application.yml` 설정 | ✅ | server.port, jwt.* 포함 |
| `application-local.yml` (개발용) | ✅ | 환경변수 기반 (`LOCAL_DB_*`) |
| `application-prod.yml` (운영용) | ✅ | 환경변수 기반, `.gitignore` 적용 |
| `.gitignore` | ✅ | application-prod.yml, .env 제외 |
| `JpaConfig` — `@EnableJpaAuditing` | ✅ | |

### Phase 1 — 글로벌 기반 ✅

| 항목 | 상태 | 파일 위치 |
|------|------|-----------|
| `ApiResponse<T>` — 공통 응답 래퍼 | ✅ | `global/response/ApiResponse.java` |
| `ErrorCode` — 전 도메인 에러 코드 enum | ✅ | `global/exception/ErrorCode.java` |
| `BusinessException` — 도메인 예외 베이스 | ✅ | `global/exception/BusinessException.java` |
| `GlobalExceptionHandler` — 전역 예외 처리 | ✅ | `global/exception/GlobalExceptionHandler.java` |
| `PageResponse<T>` — 페이지네이션 응답 | ✅ | `global/response/PageResponse.java` |

### Phase 2 — 인증/인가 (user/auth 도메인) ✅

**1~9단계 구현 완료.** 카카오 OAuth 로그인 왕복(302·쿠키)부터 JWT 인증 필터, 회원 API,
탈퇴 오케스트레이션까지 동작한다. 자동 테스트 **13개 클래스 131개 케이스 통과**.

| 패키지 | 구현물 |
|--------|--------|
| `domain/user` | `User`·`Role`·`UserStatus`·`Provider`, `UserRepository`, `UserService`, `UserController` |
| `domain/auth` | `RefreshToken`, `KakaoOAuthClient`, `JwtProvider`·`SecureTokenGenerator`, `JwtProperties`·`KakaoProperties`·`RestClientConfig`, `AuthService`, `AuthController` |
| `global/security` | `SecurityConfig`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`, `CustomUserDetailsService`, `UserPrincipal` |
| `global/util` | `CookieUtil` — `refresh_token`·`oauth_state` 두 쿠키의 속성을 여기서만 만든다 |

스키마는 Flyway가 `V1__init.sql`로 소유한다(`ddl-auto: validate`로 local·prod 통일).

**브라우저 E2E도 통과했다(2026-08-26).** 자동 테스트가 덮지 못하는 셋 — MySQL Flyway 적용,
브라우저의 실제 쿠키 저장·전송·삭제, 카카오 실서버 왕복 — 을 실제 카카오 계정으로 확인했다.
근거는 [[phase2-auth]] 각 항목 체크포인트에 적혀 있다.

각 클래스 Javadoc 첫 줄의 `[N단계 · G]` 표기가 소속 단계다.
엔티티(1) → Flyway(2) → Repository(3) → 카카오 연동(4) → 토큰 부품(5) → Security 배선(6) → 서비스(7) → 로그인 API(8) → 회원 API(9).

### Phase 3 — 카테고리 / 상품 ⬜

### Phase 4 — 구매 플로우 (Cart · Order · Payment) ⬜

### Phase 5 — 리뷰 ⬜

---

## 7. 미결 사항

- [x] 소셜 로그인 — **카카오 전용으로 확정** (구글 등 추가 provider는 `Provider` enum 상수 추가로 확장)
- [ ] ADMIN 계정 발급 방식 ([[phase2-auth]] 향후 개선 항목)
- [ ] 상품 이미지 저장 방식 (로컬 / AWS S3)
- [ ] 결제 외부 PG사 연동 여부 (포트원 등) — 현재 Mock 처리
- [ ] Redis 도입 여부 (Refresh Token 저장, 인기 상품 캐싱)
- [ ] 쿠폰 / 포인트 도메인 추가 여부
- [ ] 검색 고도화 (Elasticsearch 등)

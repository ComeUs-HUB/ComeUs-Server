# 아키텍처

시스템 전체의 골격 — 기술 스택, 패키지 구조, 의존 규칙, 전 도메인이 공유하는 기술 결정.

도메인별 스펙은 [domains/](domains/), 응답·에러 규약은 [conventions/](conventions/),
결정 근거는 [decisions/](decisions/), 진행 현황은 [roadmap.md](roadmap.md)에 있다.
그림 요약은 [diagrams/](diagrams/README.md) — [시스템 구성도](diagrams/global/system-overview.md)·[요청 처리 파이프라인](diagrams/global/request-pipeline.md)부터 보면 된다.

## 개요

| 항목 | 내용 |
|---|---|
| 프로젝트 | 커머스 플랫폼 백엔드 REST API |
| 기술 스택 | Spring Boot 3.5.0 · Java 21 · JPA · MySQL · Gradle (Kotlin DSL) |
| 인증 | 카카오 OAuth + JWT ([ADR 0001](decisions/auth/0001-kakao-only-oauth.md)) |
| 스키마 | Flyway 소유, Hibernate는 `validate`만 ([ADR 0008](decisions/platform/0008-flyway-owns-schema.md)) |
| 테스트 DB | H2 인메모리 (`MODE=MySQL`, `ddl-auto: create-drop`, Flyway 비활성) |

## 도메인

| 도메인 | 설명 | 문서 |
|---|---|---|
| User | 회원 프로필 · 생명주기(가입·탈퇴) | [domains/auth.md](domains/auth.md) |
| Auth | 카카오 로그인 · JWT/Refresh Token 발급·검증 | 〃 |
| Category | 상품 카테고리 | [domains/catalog/category.md](domains/catalog/category.md) |
| Product | 상품 CRUD · 검색 | [domains/catalog/product.md](domains/catalog/product.md) |
| Cart | 장바구니 | [domains/purchase/cart.md](domains/purchase/cart.md) |
| Order | 주문 생성·조회·취소 | [domains/purchase/order.md](domains/purchase/order.md) |
| Payment | 결제 · 환불 | [domains/purchase/payment.md](domains/purchase/payment.md) |
| Review | 상품 리뷰 | [domains/review.md](domains/review.md) |

## 패키지 구조

```
com.toyproject.backend
├── domain
│   ├── user                    # 회원 프로필 · 생명주기
│   │   ├── controller          #   UserController
│   │   ├── service             #   UserService
│   │   ├── repository          #   UserRepository
│   │   ├── entity              #   User, Role, UserStatus, Provider
│   │   ├── dto                 #   UserResponse, UserUpdateRequest, KakaoUserResolution
│   │   └── exception           #   UserErrorCode
│   ├── auth                    # 카카오 로그인 · 토큰
│   │   ├── controller          #   AuthController
│   │   ├── service             #   AuthService
│   │   ├── client              #   KakaoOAuthClient (+dto)
│   │   ├── token               #   JwtProvider, SecureTokenGenerator
│   │   ├── config              #   JwtProperties, KakaoProperties, RestClientConfig
│   │   ├── entity              #   RefreshToken
│   │   ├── repository          #   RefreshTokenRepository
│   │   ├── dto                 #   TokenResponse, KakaoLoginResult
│   │   └── exception           #   AuthErrorCode
│   ├── category                # 카테고리 (2depth 트리)
│   │   ├── controller          #   CategoryController
│   │   ├── service             #   CategoryService
│   │   ├── repository          #   CategoryRepository
│   │   ├── entity              #   Category (self-join)
│   │   ├── dto                 #   CategoryCreateRequest, CategoryUpdateRequest,
│   │   │                       #   CategoryResponse, CategoryTreeResponse
│   │   └── exception           #   CategoryErrorCode
│   ├── product                 # 상품 (페이징·필터·soft delete)
│   │   ├── controller          #   ProductController
│   │   ├── service             #   ProductService
│   │   ├── repository          #   ProductRepository, ProductSpecifications
│   │   ├── entity              #   Product, ProductImage, ProductStatus
│   │   ├── dto                 #   ProductListResponse, ProductDetailResponse,
│   │   │                       #   ProductSearchRequest, ProductSearchCondition
│   │   └── exception           #   ProductErrorCode
│   ├── cart        ─┐
│   ├── order        ├─ exception/*ErrorCode 만 존재. 나머지는 착수 시점에 만든다
│   ├── payment      │
│   └── review      ─┘
└── global
    ├── config       # SecurityConfig, AppProperties, JpaConfig
    ├── exception    # ErrorCode(interface), CommonErrorCode,
    │                #   BusinessException, GlobalExceptionHandler
    ├── response     # ApiResponse, ErrorResponse, PageResponse
    ├── security     # JwtAuthenticationFilter, JwtAuthenticationEntryPoint,
    │                #   JwtAccessDeniedHandler, CustomUserDetailsService, UserPrincipal
    └── util         # CookieUtil
```

**도메인 패키지는 미리 만들어두지 않는다.** 착수 시점에 필요한 하위 폴더(`controller`, `service`,
`repository`, `entity`, `dto`)부터 실제 클래스와 함께 만든다.

## 의존 규칙

- **`domain → global` 단방향.** `global`의 **재사용 부품** — `global/response`·`global/exception`·
  `global/util` — 은 `domain`을 import하지 않는다. 어느 도메인에서도 쓰일 수 있어야 하기 때문이다
  - **예외는 Spring Security 배선뿐이다**: `global/security` 전체와 `global/config/SecurityConfig`.
    프레임워크가 도메인 타입(`User`, `JwtProvider`, `AuthErrorCode`)을 직접 요구하는 결합 지점이라
    피할 수 없다. `SecurityConfig`는 `JwtProvider`를 주입받아 `JwtAuthenticationFilter`를 조립한다
  - 이 규칙은 한 줄로 판정된다:
    ```bash
    grep -rn "^import com.toyproject.backend.domain" src/main/java/com/toyproject/backend/global/ \
      | grep -v "global/security/\|global/config/SecurityConfig.java"
    #   → 비어야 한다
    ```
- **도메인 간 참조는 방향을 고정하고, 리포지토리가 아니라 서비스를 경유한다.** 아래가 전부다
  - `auth → user` — `UserRepository`가 아니라 `UserService`의 공개 메서드만 쓴다
    ([domains/auth.md](domains/auth.md))
  - `user → auth` — 탈퇴가 토큰 정리까지 끝내야 해서 `UserController`가 `AuthService`의
    `revokeAllTokens`·`unlinkKakao`를 함께 부른다. `UserService`는 `AuthErrorCode`만 쓴다
    ([domains/auth.md](domains/auth.md))
  - `product → category` — `Product`가 `Category`를 참조하고, 목록 필터의 하위 id를
    `CategoryService`에서 받는다. `CategoryRepository`는 category 패키지 밖에서 주입하지 않는다
  - `category → product` — **역방향이며 카테고리 삭제 차단(CA004) 하나에만** 열려 있다.
    유일하게 Service가 아니라 Repository(`ProductRepository.existsByCategoryId`)를 직접 읽는데,
    Service를 경유하면 생성자 주입 순환이 된다
    ([ADR 0014](decisions/catalog/0014-category-reads-product-repository.md))
  - **아래 둘은 Phase 4 착수 시점에 들어온다.**
  - 엔티티 참조 다섯 — `cart → user`(`Cart.user`), `cart → product`(`CartItem.product`),
    `order → user`(`Order.user`), `order → product`(`OrderItem.product`),
    `payment → order`(`Payment.order`). `product → category`와 같은 모양이고, 참조할 엔티티는
    그 도메인의 **Service에서 받는다**(`ProductService`가 `categoryService.getCategory()`를
    쓰는 것과 같다). Phase 5의 review도 같은 방식으로 `user`·`product`·`order`를 참조한다
    ([domains/purchase/README.md](domains/purchase/README.md))
  - `order → cart` — **서비스 호출로 생기는 유일한 의존이다.** `OrderService.create()` 하나가
    `CartService`를 경유한다. 순환이 없어 `category → product`처럼 Repository로 내릴 이유가 없고,
    주문이 장바구니에서 **읽고 지우는** 둘을 하므로 빈 장바구니 처리 같은 규칙이 `CartService`에
    모여 있어야 한다
    ([ADR 0017](decisions/purchase/0017-order-copies-cart-and-cuts-the-link.md))
- `BusinessException`이 `ErrorCode`를 **인터페이스 타입**으로 들고 있어, 새 도메인이 에러 코드를
  추가해도 `global`을 고치지 않는다 ([conventions/error-codes.md](conventions/error-codes.md))

## 인증 · 인가

- Spring Security 6.x `SecurityFilterChain` 방식 (`WebSecurityConfigurerAdapter` 미사용)
- `sessionManagement(STATELESS)`, `csrf().disable()`, `formLogin`·`httpBasic`·`logout` disable
- `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
- JWT Access Token(30분) + Refresh Token(7일)
- **Access Token은 응답 바디 → FE 메모리**, **Refresh Token은 `HttpOnly` 쿠키**.
  어떤 토큰도 `localStorage`/`sessionStorage`에 저장하지 않고, access token을 URL에 싣지 않는다
- **쿠키는 2종이고 `SameSite`가 서로 다르다** — 자격증명인 `refresh_token`은 **`Strict`**,
  OAuth 왕복용 `oauth_state`는 **`Lax`**. `Path`·`Max-Age` 등 나머지 속성과 근거는
  [domains/auth.md](domains/auth.md)의 쿠키 표에 있다
- Refresh Token은 DB `refresh_tokens`에 SHA-256 해시로 저장 (추후 Redis 교체 가능)
  — [ADR 0005](decisions/auth/0005-opaque-refresh-token-hashed-in-db.md)
- **인증 실패(401)는 `JwtAuthenticationEntryPoint`, 인가 실패(403)는 `JwtAccessDeniedHandler`**가 맡는다
  — [ADR 0010](decisions/platform/0010-access-denied-handler-for-authorization.md) (Phase 3에서
  [ADR 0007](decisions/platform/0007-single-entrypoint-for-401-403.md)을 대체).
  ⚠️ **익명 요청은 권한 부족이어도 EntryPoint로 간다** — 필터가 실패 시 `SecurityContext`를 비우기 때문이다.
  토큰 없이 ADMIN 경로를 호출하면 403이 아니라 **401(A001)**이다
- Refresh Token Rotation 미적용 — [ADR 0004](decisions/auth/0004-no-refresh-token-rotation.md)

**CORS**: `allowCredentials(true)` 필수(쿠키 왕복). `allowedOrigins`는 `app.cors.allowed-origins`
명시 목록. CORS가 필요한 것은 `/refresh`·`/logout`·`/users/me` **뿐**이다 —
`/kakao/authorize`·`/kakao/callback`은 XHR이 아니라 브라우저 top-level 네비게이션이라 CORS가 적용되지 않는다.

⚠️ **`oauth_state`를 `Strict`로 바꾸면 로그인이 전부 실패한다.** 카카오 콜백은 `kauth.kakao.com` →
API 호스트로의 **cross-site top-level GET 네비게이션**이라 `Strict` 쿠키가 전송되지 않는다. 그러면
`AuthController.matchesState`가 `false`를 반환해 A009로 리다이렉트되고 로그인이 중단된다.
`Lax`는 top-level GET에 한해 전송되므로 `oauth_state`는 **반드시 `Lax`여야 한다.**

**CSRF 토큰을 두지 않는 근거**: 자격증명 쿠키인 **`refresh_token`이 `SameSite=Strict`**라
cross-site 요청에 붙지 않아 원천 차단된다. (`oauth_state`는 자격증명이 아니라 CSRF 방어값 자체이므로
이 논증과 무관하다. 인증된 API는 쿠키가 아닌 `Bearer` 토큰을 쓴다.)
⚠️ 이 근거와, **FE/BE를 동일 상위 도메인에 배포**한다는 전제는 **둘 다 `refresh_token`의 `Strict`에서만
파생된다.** 전제가 깨져 `refresh_token`을 `SameSite=None`으로 바꾸면 CSRF 토큰 도입이 필수가 된다
— [ADR 0006](decisions/auth/0006-cookie-samesite-and-path.md), [deployment.md](deployment.md).

## RBAC 권한 매트릭스

| 대상 | 비회원 | USER | ADMIN |
|---|---|---|---|
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

**카테고리·상품 네 행은 필터체인에 실제로 배선돼 있다** — `GET /api/categories`와
`GET /api/products`·`/api/products/{id}`가 permitAll이고, 양쪽 쓰기 셋은 `hasRole("ADMIN")`이다.
남은 것은 리뷰뿐이며, 명시된 매처가 없어 `anyRequest().authenticated()`에 걸린다 —
리뷰의 공개 조회는 아직 열려 있지 않다.
위 매트릭스와 실제 필터체인을 엔드포인트 단위로 대조하는 작업은 전 도메인 구현 후에 한다
([roadmap.md](roadmap.md)).

컨트롤러·필터체인 방어에 더해 **"본인 리소스만 접근 가능"류는 서비스 계층에도 검증을 둔다**
(리뷰 수정·삭제, 주문 조회 등). 필터체인은 "로그인했는가"까지만 판정할 수 있다.

## 응답 · 예외 · 페이지네이션

모든 API가 `{success, data, error}` 포맷을 따른다. 예외는 `GlobalExceptionHandler`가 7종을 처리하고,
목록 조회는 `PageResponse<T>`로 감싼다. 상세는 [conventions/api-response.md](conventions/api-response.md).

예외 계층:

```
RuntimeException
└── BusinessException (ErrorCode 보유)
    └── 도메인별 구체 예외 (필요해질 때 추가)
```

유효성 검증은 `spring-boot-starter-validation` + 컨트롤러 파라미터 `@Valid`.

## 설정

- `application.yml` — 공통 베이스. **커밋된다.** 비밀값을 넣지 않는다
  - `spring.config.import: optional:file:./.env[.properties]` — 로컬 비밀값을 루트 `.env`에서 읽는다.
    이 한 줄이 없으면 실행 주체(IDE / 터미널 / `java -jar`)마다 환경변수를 따로 주입해야 한다.
    `optional:`이 있어야 `.env`가 없는 환경(CI·운영)에서도 기동된다
- `application-local.yml` / `application-prod.yml` — 프로파일별. **둘 다 `.gitignore` 대상**이다.
  `datasource`와 모든 비밀값이 여기 있다
- `src/test/resources/application.yml` — 테스트 전용. **main의 `application.yml`을 shadow하므로,
  여기 없는 키는 테스트 컨텍스트에 존재하지 않는다.** `@ConfigurationProperties` 빈이 늘어날 때마다
  이 파일에 더미 값을 함께 추가해야 컨텍스트 로딩이 깨지지 않는다

활성 프로파일 이름은 **`local`**이다(`dev` 아님).

# 아키텍처

시스템 전체의 골격 — 기술 스택, 패키지 구조, 의존 규칙, 전 도메인이 공유하는 기술 결정.

도메인별 스펙은 [domains/](domains/), 응답·에러 규약은 [conventions/](conventions/),
결정 근거는 [decisions/](decisions/), 진행 현황은 [roadmap.md](roadmap.md)에 있다.

## 개요

| 항목 | 내용 |
|---|---|
| 프로젝트 | 의류 판매 웹사이트 백엔드 REST API |
| 기술 스택 | Spring Boot 3.5.0 · Java 21 · JPA · MySQL · Gradle (Kotlin DSL) |
| 인증 | 카카오 OAuth + JWT ([ADR 0001](decisions/0001-kakao-only-oauth.md)) |
| 스키마 | Flyway 소유, Hibernate는 `validate`만 ([ADR 0008](decisions/0008-flyway-owns-schema.md)) |
| 테스트 DB | H2 인메모리 (`MODE=MySQL`, `ddl-auto: create-drop`, Flyway 비활성) |

## 도메인

| 도메인 | 설명 | 문서 |
|---|---|---|
| User | 회원 프로필 · 생명주기(가입·탈퇴) | [domains/auth.md](domains/auth.md) |
| Auth | 카카오 로그인 · JWT/Refresh Token 발급·검증 | 〃 |
| Category | 상품 카테고리 | [domains/catalog.md](domains/catalog.md) |
| Product | 상품 CRUD · 검색 | 〃 |
| Cart | 장바구니 | [domains/purchase.md](domains/purchase.md) |
| Order | 주문 생성·조회·취소 | 〃 |
| Payment | 결제 · 환불 | 〃 |
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
│   ├── product     ─┐
│   ├── category     │
│   ├── cart         ├─ exception/*ErrorCode 만 존재. 나머지는 착수 시점에 만든다
│   ├── order        │
│   ├── payment      │
│   └── review      ─┘
└── global
    ├── config       # SecurityConfig, AppProperties, JpaConfig
    ├── exception    # ErrorCode(interface), CommonErrorCode,
    │                #   BusinessException, GlobalExceptionHandler
    ├── response     # ApiResponse, ErrorResponse, PageResponse
    ├── security     # JwtAuthenticationFilter, JwtAuthenticationEntryPoint,
    │                #   CustomUserDetailsService, UserPrincipal
    └── util         # CookieUtil
```

**도메인 패키지는 미리 만들어두지 않는다.** 착수 시점에 필요한 하위 폴더(`controller`, `service`,
`repository`, `entity`, `dto`)부터 실제 클래스와 함께 만든다.

## 의존 규칙

- **`domain → global` 단방향.** `global`이 `domain.*`를 import하면 안 된다
  - 예외: `global/security`는 Spring Security 어댑터라 도메인을 참조한다(프레임워크 결합 지점).
    그 외 `global` 패키지에는 적용되지 않는다
- **도메인 간 참조는 단방향으로 유지한다.** 현재 유일한 도메인 간 의존은 `auth → user`이며,
  auth는 `UserRepository`가 아니라 `UserService`의 공개 메서드만 경유한다
  ([domains/auth.md](domains/auth.md))
- `BusinessException`이 `ErrorCode`를 **인터페이스 타입**으로 들고 있어, 새 도메인이 에러 코드를
  추가해도 `global`을 고치지 않는다 ([conventions/error-codes.md](conventions/error-codes.md))

## 인증 · 인가

- Spring Security 6.x `SecurityFilterChain` 방식 (`WebSecurityConfigurerAdapter` 미사용)
- `sessionManagement(STATELESS)`, `csrf().disable()`, `formLogin`·`httpBasic`·`logout` disable
- `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
- JWT Access Token(30분) + Refresh Token(7일)
- **Access Token은 응답 바디 → FE 메모리**, **Refresh Token은 `HttpOnly` 쿠키**.
  어떤 토큰도 `localStorage`/`sessionStorage`에 저장하지 않고, access token을 URL에 싣지 않는다
- Refresh Token은 DB `refresh_tokens`에 SHA-256 해시로 저장 (추후 Redis 교체 가능)
  — [ADR 0005](decisions/0005-opaque-refresh-token-hashed-in-db.md)
- 401·403은 `JwtAuthenticationEntryPoint` 하나가 전담. `AccessDeniedHandler`를 두지 않는다
  — [ADR 0007](decisions/0007-single-entrypoint-for-401-403.md)
- Refresh Token Rotation 미적용 — [ADR 0004](decisions/0004-no-refresh-token-rotation.md)

**CORS**: `allowCredentials(true)` 필수(쿠키 왕복). `allowedOrigins`는 `app.cors.allowed-origins`
명시 목록. CORS가 필요한 것은 `/refresh`·`/logout`·`/users/me` **뿐**이다 —
`/kakao/authorize`·`/kakao/callback`은 XHR이 아니라 브라우저 top-level 네비게이션이라 CORS가 적용되지 않는다.

**CSRF 토큰을 두지 않는 근거**: `SameSite=Strict`가 cross-site 요청에 쿠키를 붙이지 않아 원천 차단된다.
⚠️ 이 전제는 **FE/BE를 동일 상위 도메인에 배포**하는 것에 의존한다. 깨지면 CSRF 토큰 도입이 필수가 된다
— [ADR 0006](decisions/0006-cookie-samesite-and-path.md), [deployment.md](deployment.md).

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

현재 필터체인은 auth·user 엔드포인트만 명시하고 나머지는 `anyRequest().authenticated()`다.
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

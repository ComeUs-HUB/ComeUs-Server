# 진행 현황

**이 파일이 진행 상태의 유일한 출처다.** 다른 문서는 상태를 적지 않는다 —
계약은 [architecture.md](architecture.md)·[domains/](domains/)·[conventions/](conventions/)가,
결정 근거는 [decisions/](decisions/)가 소유한다.

> 기준: 2026-09-07 · 브랜치 `feature/phase2-auth`

## 요약

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 환경 설정 — 프로젝트 골격, 의존성, 설정 파일 | ✅ |
| 1 | 글로벌 기반 — 공통 응답, 예외 처리, 페이지네이션 | ✅ |
| 2 | 인증/인가 — user/auth 도메인, Security, JWT | ✅ (브라우저 E2E 통과) |
| 3 | 카테고리 / 상품 | ⬜ |
| 4 | 구매 플로우 — Cart · Order · Payment | ⬜ |
| 5 | 리뷰 | ⬜ |
| 6 | 마무리 — RBAC 최종 점검 | ⬜ |
| 7 | 배포 — Docker, CI/CD, 서버, 모니터링 | ⬜ ([deployment.md](deployment.md)) |

**다음 작업은 Phase 3 (Category)** — [domains/catalog.md](domains/catalog.md)의 "착수 전 정할 것"부터.

**현재 규모**: main 48개 파일 · 테스트 13개 클래스 137개 케이스.

## Phase 0 — 환경 설정 ✅

Spring Boot 3.5.0 + Java 21 + Gradle(Kotlin DSL) 골격, 의존성, 프로파일별 설정이 모두 갖춰졌다.

- 활성 프로파일은 **`local`**이다(설계 초안의 `dev`가 아님)
- `application-local.yml`·`application-prod.yml`은 `.gitignore` 대상 — 의도대로 동작 확인
- `src/test/resources/application.yml`에 H2 인메모리 테스트 설정 준비됨
- `JpaConfig`의 `@EnableJpaAuditing`, `BackendApplication`의 `@ConfigurationPropertiesScan` 적용
- Flyway(`flyway-core` + `flyway-mysql`)는 Phase 2에서 함께 도입됨
  ([ADR 0008](decisions/0008-flyway-owns-schema.md))

도메인 패키지는 **미리 만들지 않는다.** `product`·`category`·`cart`·`order`·`payment`·`review`에는
현재 `exception/*ErrorCode`만 있고, 나머지 하위 폴더는 각 Phase 착수 시점에 만든다.
이는 미완이 아니라 [architecture.md](architecture.md)에 적힌 방침이다.

남은 것:
- [ ] `management.endpoints`(actuator) 설정 — 배포 단계에서 필요해질 때 추가

## Phase 1 — 글로벌 기반 ✅

전 도메인이 공유하는 응답·예외·페이지네이션 구조가 준비됐다.
스펙은 [conventions/api-response.md](conventions/api-response.md)에 있다.

| 구현물 | 위치 |
|---|---|
| `ApiResponse<T>` | `global/response/ApiResponse.java` |
| `ErrorResponse` (+ `FieldError`) | `global/response/ErrorResponse.java` |
| `PageResponse<T>` | `global/response/PageResponse.java` |
| `ErrorCode` (interface) + `CommonErrorCode` | `global/exception/` |
| 도메인별 `*ErrorCode` 8종 | `domain/*/exception/` |
| `BusinessException` | `global/exception/BusinessException.java` |
| `GlobalExceptionHandler` — 7종 처리 | `global/exception/GlobalExceptionHandler.java` |

남은 것:
- [ ] `PageResponse`를 실제로 쓰는 목록 조회 API — Phase 3 Product에서 처음 적용된다
- [ ] `BusinessException`을 상속하는 도메인별 구체 예외 클래스 — 필요해지는 시점에

## Phase 2 — 인증/인가 ✅

카카오 OAuth 로그인 왕복(302·쿠키)부터 JWT 인증 필터, 회원 API, 탈퇴 오케스트레이션까지 동작한다.
계약은 [domains/auth.md](domains/auth.md), 결정 근거는 [decisions/](decisions/)에 있다.

구현 순서는 9단계로 나눴고, 각 클래스 Javadoc 첫 줄에 `[N단계 · G]` 형태로 소속 단계가 표기돼 있다.

| 단계 | 내용 | 검증 |
|---|---|---|
| 1 | 엔티티 (`User`, `RefreshToken`) | 2단계에서 함께 |
| 2 | Flyway + 스키마 (`V1__init.sql`) | MySQL 기동 시 `validate` 통과 |
| 3 | Repository | `UserRepositoryTest`, `RefreshTokenRepositoryTest` |
| 4 | 카카오 연동 (`KakaoOAuthClient`) | `KakaoOAuthClientTest` |
| 5 | 토큰 부품 (`JwtProvider`, `SecureTokenGenerator`) | `JwtProviderTest`, `SecureTokenGeneratorTest` |
| 6 | Security 배선 + JWT 필터 | `JwtAuthenticationIntegrationTest` |
| 7 | 서비스 (`UserService`, `AuthService`) | `UserServiceTest`, `AuthServiceTest`, `KakaoLoginTransactionTest` |
| 8 | 로그인 API | `AuthControllerTest`, `CookieUtilTest` + 브라우저 |
| 9 | 회원 API + 탈퇴 | `UserControllerTest` + 브라우저 E2E |

### 브라우저 E2E (2026-08-26)

실제 카카오 계정으로 로그인 → silent refresh → `/api/users/me` GET·PUT·DELETE → 탈퇴 →
재로그인 재활성화까지 왕복했다. 자동 테스트가 덮지 못하는 셋을 여기서 확인했다:
① MySQL Flyway 적용 ② 브라우저의 실제 쿠키 저장·전송·삭제 ③ 카카오 실서버 왕복.

- [x] 카카오 개발자 콘솔 Redirect URI 등록 (local). **prod는 배포 시 필요**
- [x] Flyway가 두 테이블 생성 + `validate` 통과 — `Schema 'toyproject' is up to date`
- [x] `/kakao/authorize` → 카카오 로그인 → `/oauth/complete` 복귀 → silent refresh → `/api/users/me` 전체 통과
- [x] `oauth_state` 쿠키가 콜백 요청에 실제로 실려 옴 (`Lax`가 아니면 여기서 깨진다)
- [x] state 불일치·누락·재사용(뒤로가기) 시 A009로 FE에 `?error=` 전달
- [x] `token_hash`에 원문이 아닌 SHA-256 해시 저장 — MySQL에서 `CHAR_LENGTH=64` 확인
- [x] 재발급 시 access token만 갱신, 응답에 `Set-Cookie` 없음
- [x] 로그아웃·탈퇴 후 같은 토큰으로 재발급 시 A007
- [x] 비인증 접근 401(A001), CORS preflight 허용/미허용 분기
- [ ] **카카오 동의 화면에서 취소 시 FE로 `?error=` 전달** — 미확인.
      이미 동의한 계정은 동의 화면이 뜨지 않아, 재현하려면 매번 연결 해제가 필요하다

### 자동 테스트 — 13개 클래스 137개 케이스

| 클래스 | 케이스 |
|---|---|
| `AuthServiceTest` | 25 |
| `AuthControllerTest` | 16 |
| `UserControllerTest` | 13 |
| `UserServiceTest` | 13 |
| `JwtAuthenticationIntegrationTest` | 12 |
| `CookieUtilTest` | 11 |
| `KakaoOAuthClientTest` | 10 |
| `RefreshTokenRepositoryTest` | 10 |
| `UserRepositoryTest` | 8 |
| `JwtProviderTest` | 7 |
| `SecureTokenGeneratorTest` | 7 |
| `KakaoLoginTransactionTest` | 4 |
| `BackendApplicationTests` | 1 (컨텍스트 로딩) |

## Phase 3 — 카테고리 / 상품 ⬜

계약: [domains/catalog.md](domains/catalog.md). 착수 전에 그 문서의 "착수 전 정할 것"을 먼저 확정한다.

## Phase 4 — 구매 플로우 ⬜

계약: [domains/purchase.md](domains/purchase.md).
**동시성 제어 방식은 정한 뒤 ADR로 남긴다** — 되돌리기 비용이 큰 결정이다.

## Phase 5 — 리뷰 ⬜

계약: [domains/review.md](domains/review.md).

## Phase 6 — 마무리 ⬜

- [ ] [architecture.md](architecture.md)의 RBAC 매트릭스와 실제 필터체인을 엔드포인트 단위로 대조
- [ ] 공개 조회 API(`/api/products`, `/api/categories`, 리뷰 목록)를 permitAll로 여는 작업 —
      현재 `anyRequest().authenticated()`가 막고 있다
- [ ] ADMIN 권한 체크 도입 시 `AccessDeniedHandler` 재검토
      ([ADR 0007](decisions/0007-single-entrypoint-for-401-403.md))

테스트는 별도 Phase로 미루지 않는다 — **각 도메인 구현과 함께 작성한다.** Phase 2가 그렇게 진행됐다.

## Phase 7 — 배포 ⬜

[deployment.md](deployment.md) 참고.

## 미결 사항

결정되면 [decisions/](decisions/)에 ADR로 남기고 여기서 지운다.

- [ ] **ADMIN 계정 발급 방식** — Phase 6에서 결정
- [ ] **PKCE 도입** — 보류 사유가 하나만 남았다.
      [ADR 0003](decisions/0003-no-pkce-yet.md)
- [ ] **Refresh Token Rotation + 재사용 탐지** — 함께 도입해야 한다.
      [ADR 0004](decisions/0004-no-refresh-token-rotation.md)
- [ ] **만료 `refresh_tokens` row 정리 배치** — `expiresAt` 검사로 거절되므로 기능상 무해
- [ ] **LOCAL 로그인** — `V2__add_password.sql` + `PasswordEncoder` + `Provider.LOCAL` +
      `AuthErrorCode`에 `INVALID_CREDENTIALS` 재추가.
      [ADR 0001](decisions/0001-kakao-only-oauth.md)
- [ ] **상품·리뷰 이미지 저장 방식** — 로컬 vs S3 등 외부 스토리지
- [ ] **외부 PG 연동** — 포트원 등. 현재 Mock 전제
- [ ] **Redis 도입** — Refresh Token 저장, 인기 상품 캐싱
- [ ] **쿠폰 / 포인트 도메인** 추가 여부
- [ ] **검색 고도화** — Elasticsearch 등

확정된 것:
- [x] 소셜 로그인은 **카카오 전용** — 구글 등 추가는 `Provider` enum 상수 추가로 확장

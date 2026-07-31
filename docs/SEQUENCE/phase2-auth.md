# Phase 2 — 인증/인가 (User 도메인, G~K)

> 상태: 미구현 — `domain/user`, `global/security` 패키지 모두 아직 없음. 단 `ErrorCode`(Auth 관련)와 `application.yml`의 `jwt.*` 설정값은 [[phase1-global-foundation]], [[phase0-environment-setup]]에서 이미 선반영되어 있음
>
> **로그인 방식 확정**: 카카오 OAuth 전용 로그인만 우선 구현한다. 이메일+비밀번호 로그인·회원가입은 이번 Phase 범위 밖이며, 필요해지는 시점에 `provider` enum 상수 추가 + 마이그레이션으로 확장한다 — 쓰지 않을 컬럼과 Bean을 미리 만들어두지 않는다(문서 하단 "향후 개선 항목" 참고). 관리자(ADMIN) 계정 발급 방식은 이번 범위 밖(미해결)으로 남긴다.
>
> **토큰 전달 방식 확정**: Access Token은 응답 바디로 내려 FE가 **메모리(JS 변수)에만** 보관하고, Refresh Token은 **`HttpOnly + Secure + SameSite=Strict` 쿠키**로 내린다. `localStorage`/`sessionStorage`에는 어떤 토큰도 저장하지 않는다 — XSS 한 번으로 7일짜리 장기 자격증명이 통째로 유출되는 경로를 차단하기 위함. **배포 전제(확정): FE와 BE를 동일 상위 도메인에 배포한다**(예: `app.example.com` / `api.example.com`, [[phase7-deployment]] X 항목). 이 전제가 깨지면 `SameSite=Strict` 쿠키가 cross-site 요청에 **아예 실리지 않아 refresh/logout이 동작 불능**이 된다 — 보안 약화가 아니라 기능 파손이다. H 항목 경고 참고. 상세 스펙은 H·K 항목.

회원 도메인과 JWT 기반 인증/인가의 기반을 만드는 단계. 이후 모든 도메인(Cart, Order, Review 등)이 "로그인한 사용자" 개념에 의존하므로 다른 도메인보다 먼저 진행되어야 한다.

---

## G. User 엔티티 / Repository / 카카오 연동 인프라 구현

**목적**: 회원 도메인의 핵심 엔티티와 카카오 OAuth 연동에 필요한 인프라 컴포넌트를 구성한다.

**User 엔티티** (`domain/user/entity/User.java`)

| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK, IDENTITY | |
| email | String | nullable, unique | 카카오 이메일 동의를 거부하면 null. MySQL은 NULL을 서로 다른 값으로 취급하므로 별도 로직 없이 "값 있을 때만 유니크" 충족 |
| name | String | nullable | 카카오 닉네임으로 초기화 |
| phone | String | nullable | `PUT /users/me`로 추후 채움 (강제 아님) |
| address | String | nullable | `PUT /users/me`로 추후 채움 (강제 아님) |
| birthDate | LocalDate | nullable | `PUT /users/me`로 추후 채움 (강제 아님) |
| role | Role(USER/ADMIN) | not null, default USER | ADMIN 발급 플로우는 범위 밖 |
| provider | Provider(**KAKAO만 정의**) | not null | LOCAL 등은 나중에 enum 상수 추가로 확장 |
| providerId | String | not null | 카카오 고유 사용자 id |
| status | UserStatus(ACTIVE/WITHDRAWN) | not null, default ACTIVE | 탈퇴는 소프트 삭제 |
| withdrawnAt | LocalDateTime | nullable | |
| createdAt/updatedAt | LocalDateTime | `@CreatedDate`/`@LastModifiedDate` | 기존 `JpaConfig`의 `@EnableJpaAuditing` 재사용, `@EntityListeners(AuditingEntityListener.class)`만 추가 |

- 유니크 제약: `email` 단일, `(provider, providerId)` 복합(동일 카카오 계정 중복 가입 방지)
- Lombok `@Getter` + `@Builder`(정적 팩토리) + protected no-args 생성자, Setter 없이 `updateProfile(phone, address, birthDate)`, `withdraw()`, `reactivate()` 도메인 메서드로 상태 변경
- `Provider`/`Role`/`UserStatus`는 별도 enum 파일로 분리

**RefreshToken 엔티티** (`domain/user/entity/RefreshToken.java`)

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | PK |
| user | User — **`@ManyToOne(fetch = FetchType.LAZY)`** + `@JoinColumn(name = "user_id", nullable = false)` | not null |
| tokenHash | `CHAR(64)` | 토큰 원문의 SHA-256 hex, unique |
| expiresAt | LocalDateTime | 만료 판정의 **유일한** 근거 |
| createdAt | LocalDateTime | `@CreatedDate` |

- **카디널리티(`user 1 : token N`)가 맞는 이유**: 로그아웃이 "해당 기기만" 무효화하므로(K 항목) 멀티 디바이스 동시 로그인이 전제이고, 유저당 여러 row가 존재해야 한다. `@OneToOne`으로 묶으면 기기 하나로 제한되어 이 정책과 충돌한다
- **`fetch = LAZY` 필수**: `@ManyToOne`의 JPA 기본값은 EAGER다. 그대로 두면 User가 필요 없는 경로(로그아웃 = 토큰 row 삭제)에서도 매번 User를 함께 로드하고, EAGER는 쿼리 단위로 무력화할 수 없어 이후 Order/Cart 등이 User를 참조하기 시작하면 연쇄 로딩으로 번진다
- **`@JoinColumn` 명시**: Flyway로 DDL을 직접 작성하고 `ddl-auto: validate`가 불일치 시 부팅을 막으므로, 컬럼명을 명시해 `V1__init.sql`과 1:1 대조가 가능해야 한다
- **토큰 형식 — 불투명(opaque) 랜덤 문자열**: `SecureRandom` 32바이트 → base64url 인코딩 43자. **JWT를 쓰지 않는다** — JWT의 존재 이유는 "DB 없이 서명만으로 검증"인데 이 설계는 DB를 조회해 유효성을 판정하므로 claim을 파싱하는 곳이 한 군데도 없다. 서명·`exp`·토큰 타입 claim이 전부 미사용인 채로 길이(수백 자)와 인덱스 부담, 만료 정보 이중화만 떠안게 된다. 불투명 문자열이면 access token(JWT)을 refresh로 오용하는 시도도 테이블에 없어 **구조적으로** 차단된다
- **DB에는 해시만 저장**: 원문은 `Set-Cookie`로만 나가고 DB에는 SHA-256 hex(64자)를 저장한다. DB가 유출되어도 그 값으로는 재발급을 받을 수 없다(비밀번호를 평문 저장하지 않는 것과 같은 논리). 우리는 토큰 원문을 다시 읽을 일이 없으므로 잃는 것이 없다
- **유효성 판정**: row 존재 **그리고** `expiresAt > now()` 두 가지를 모두 확인한다. JWT를 걷어냈으므로 `expiresAt`이 만료의 유일한 근거이며, row 존재만 보고 통과시키면 만료된 토큰이 영구히 유효해진다
- **재발급 시 refresh row는 건드리지 않는다**(rotation 미적용): 7일 고정으로 두고 access token만 새로 발급한다. rotation의 실익은 재사용 탐지(reuse detection)와 짝을 이룰 때 나오는데 탐지는 이번 범위 밖이고, 탐지 없는 rotation은 멀티탭 경합 대응(서버측 grace period, FE 단일 Promise 묶기)이라는 파생 복잡도만 남긴다. 쿠키를 `HttpOnly + Secure + SameSite=Strict + Path=/api/auth`로 좁혀둔 상태라 한계 효용도 작다 — 향후 개선 항목 참고
- 로그아웃: 해당 row만 삭제(다른 기기 세션 유지)
- 탈퇴: 해당 유저의 전체 row 삭제

**Repository**
- `UserRepository`: `findByProviderAndProviderId(Provider, String)`, `existsByEmail(String)`
- `RefreshTokenRepository`
  - `findByTokenHash(String)` — `@EntityGraph(attributePaths = "user")`로 User를 join fetch. 재발급 흐름에서 새 access token의 id·role claim과 WITHDRAWN 여부 확인에 **항상** User가 필요하므로, LAZY 지연 초기화에 맡겨 쿼리를 2번 내지 않는다
  - `deleteByTokenHash(String)` — `@Modifying @Query` 벌크 삭제
  - `deleteAllByUser(User)` — `@Modifying @Query` 벌크 삭제
  - ⚠️ Spring Data JPA의 **파생 삭제 메서드는 select-then-delete로 동작**한다(엔티티를 먼저 SELECT한 뒤 row별 DELETE). 탈퇴 시 토큰 N개를 조회 후 N번 삭제하게 되므로 위 두 삭제는 반드시 벌크 쿼리로 작성할 것

**카카오 연동 인프라**
- `global/config/KakaoProperties`(`@ConfigurationProperties(prefix="kakao")`): `clientId, clientSecret, adminKey, tokenUri, userInfoUri, unlinkUri, redirectUri`
- **`redirectUri`는 서버 설정값으로 고정한다** — FE에게서 받지 않는다. 카카오 개발자 콘솔에 등록된 값은 어차피 서버가 아는 값이고, 서버가 고정하면 화이트리스트 검증 로직 자체가 불필요해진다. 요청 필드로 받으면 반드시 화이트리스트 대조가 따라붙는데, 선택지를 없애는 쪽이 더 단순하고 더 안전하다
- `global/config/RestClientConfig`: `RestClient.Builder`로 kauth/kapi용 RestClient Bean 2개 등록 (신규 Gradle 의존성 불필요 — `RestClient`는 `spring-boot-starter-web`에 이미 포함, `spring-boot-starter-oauth2-client`/webflux 둘 다 사용 안 함)
- `domain/user/client/KakaoOAuthClient`: `requestToken(code)`, `fetchUserInfo(kakaoAccessToken)`, `unlink(providerId)`. 카카오 호출 실패는 이 경계에서 `BusinessException(KAKAO_AUTH_FAILED/KAKAO_API_ERROR)`로 변환해 상위 계층이 카카오의 존재를 몰라도 되게 함
- 토큰 요청 form 바디: `grant_type=authorization_code`, `client_id`, `client_secret`, `redirect_uri`(설정값), `code`
- `domain/user/client/dto/KakaoTokenResponse`, `KakaoUserInfoResponse`(+ 중첩 `KakaoAccount`, `Profile`) — record + `@JsonNaming(SnakeCaseStrategy)`로 카카오 snake_case 응답 자동 매핑
- 호출하는 카카오 API: 토큰 발급 `POST https://kauth.kakao.com/oauth/token`, 사용자 정보 `GET https://kapi.kakao.com/v2/user/me`, 연결 해제 `POST https://kapi.kakao.com/v1/user/unlink`(Admin Key 방식)

**Flyway 마이그레이션 도입(신규)**
- Phase 3~5에서 Product/Category/Cart/Order/Payment/Review 테이블이 계속 늘어나므로, 테이블이 2개뿐인 이 시점에 Flyway를 들여 스키마 변경을 이력 관리한다 — 뒤로 미룰수록 "local에서 Hibernate가 자동 생성한 스키마를 손으로 prod DDL에 반영"하는 작업의 위험이 테이블 수만큼 커진다([[phase0-environment-setup]] B/C 항목 참고).
- `build.gradle.kts`에 `org.flywaydb:flyway-core`, `org.flywaydb:flyway-mysql` 추가.
- `application.yml`(공통)에 `spring.flyway.enabled: true`, `spring.flyway.locations: classpath:db/migration` 추가.
- `application-local.yml`/`application-prod.yml`의 `ddl-auto`를 둘 다 `validate`로 통일(local의 기존 `update`는 제거) — 이 시점부터 Hibernate는 스키마를 검증만 하고, 실제 테이블 생성/변경은 마이그레이션 스크립트가 전담.
- `src/main/resources/db/migration/V1__init.sql` 작성: `users`, `refresh_tokens` 테이블 DDL(위 엔티티 표 기준 — 컬럼, nullable, `email` unique, `(provider, provider_id)` 복합 unique, `refresh_tokens.token_hash CHAR(64)` unique, `user_id` FK 등)을 엔티티와 1:1로 맞춰 직접 작성. 토큰을 해시로 저장하므로 unique 인덱스 키가 64바이트로 고정된다(JWT 원문을 `VARCHAR(500)`으로 저장했다면 utf8mb4 기준 최대 2000바이트). 이후 Phase 3~5에서 테이블이 추가/변경될 때마다 `V2__`, `V3__` ... 순서로 새 마이그레이션 파일만 추가하고 기존 파일은 수정하지 않는 규칙을 지킨다.

**체크포인트**
- [ ] `flyway-core`/`flyway-mysql` 의존성 추가, `spring.flyway.*` 설정 반영
- [ ] `ddl-auto`를 local/prod 모두 `validate`로 통일
- [ ] `V1__init.sql` 작성 및 `./gradlew bootRun` 시 Flyway가 `users`, `refresh_tokens` 테이블을 정상 생성하는지 확인 (local/prod 동일 스크립트로 스키마 일치 보장)
- [ ] `(provider, providerId)` 중복 가입 방지 확인
- [ ] `KakaoOAuthClient`가 카카오 API 실패 시 `BusinessException`으로 변환하는지 확인 (`MockRestServiceServer`로 목킹 테스트)

---

## H. Spring Security 기반 설정

**목적**: Spring Security 6.x 방식(`SecurityFilterChain` Bean)으로 인증/인가 기반을 완성한다.

**개발 방향**
- `global/config/SecurityConfig`, `global/security/JwtAuthenticationEntryPoint`(401), `global/security/JwtAccessDeniedHandler`(403)
- 엔드포인트 정책 매트릭스:

| 엔드포인트 | 인증 |
|---|---|
| `POST /api/auth/kakao` | permitAll |
| `POST /api/auth/refresh` | permitAll (`refresh_token` 쿠키 자체가 자격증명) |
| `POST /api/auth/logout` | permitAll (access token 만료 상태에서도 쿠키만으로 로그아웃 가능해야 UX상 자연스러움) |
| `GET/PUT/DELETE /api/users/me` | authenticated |
| 그 외 전체 | `anyRequest().authenticated()` (Product/Category 등 공개 조회 API가 생기면 그때 permitAll 매처 추가 — [[phase6-finalization]] R 항목에서 최종 점검) |

- `csrf().disable()`, `formLogin/httpBasic/logout` 모두 disable(JWT 전용), `sessionManagement(STATELESS)`
- **CSRF 방어 근거(중요)**: refresh token을 쿠키로 옮기면 `POST /api/auth/refresh`·`/logout`이 CSRF 표적이 된다 — 공격자 사이트가 POST를 유발하면 브라우저가 쿠키를 자동 첨부하기 때문. 응답 자체는 CORS 때문에 읽을 수 없어 토큰 탈취는 불가능하지만, 원치 않는 로그아웃을 유발당하는 문제가 남는다. 이를 **`SameSite=Strict`가 cross-site 요청에 쿠키를 붙이지 않음으로써 원천 차단**하므로 CSRF 토큰을 별도로 두지 않는다
- ⚠️ **`SameSite=Strict`는 "FE/BE가 동일 상위 도메인"이라는 배포 전제에 전적으로 의존한다**([[phase7-deployment]] X 항목에서 확정). FE를 다른 사이트(Vercel 등)로 분리하면 브라우저가 cross-site 요청에 refresh 쿠키를 **아예 첨부하지 않아 `/refresh`·`/logout`이 전면 동작 불능**이 된다 — CSRF 방어가 약해지는 정도가 아니라 인증 기능 자체가 깨진다. 분리 배포가 필요해지면 `SameSite=None; Secure`로 전환하고 **동시에** CSRF 토큰을 반드시 도입해야 한다
- `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
- 401/403 응답도 프로젝트 공통 `ApiResponse`/`ErrorResponse` 포맷을 따르도록 `JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler`에서 `ObjectMapper`로 직접 JSON 작성(필터 단계라 `GlobalExceptionHandler`를 타지 않음). 이때 **HTTP 상태를 하드코딩하지 말고 `errorCode.getHttpStatus()`를 그대로 사용**한다 — 인증 경로로 들어와도 403인 코드(`WITHDRAWN_USER` U010)가 있기 때문(J 항목 매핑표 참고)
- CORS: refresh token을 쿠키로 주고받으므로 **`allowCredentials(true)` 필수**. 이때 `allowedOrigins`에 와일드카드 `*`를 쓸 수 없으므로(CORS 스펙상 금지) `app.cors.allowed-origins`의 명시적 오리진 목록을 그대로 사용한다. FE는 인증 관련 요청에 `credentials: 'include'`를 붙여야 한다

**체크포인트**
- [ ] 비회원 허용 API(`/api/auth/*`) 정상 접근 확인
- [ ] 인증 필요 API에 비인증 접근 시 401(U003) 반환
- [ ] ADMIN 전용 API에 일반 회원 접근 시 403 반환 (Phase 2 시점엔 ADMIN 전용 API 없음 — Phase 3 이후 도메인에서 검증)
- [ ] FE 오리진에서 `credentials: 'include'` 요청 시 CORS 통과 및 쿠키 왕복 확인 (preflight 포함)
- [ ] 허용되지 않은 오리진에서의 요청이 CORS로 차단되는지 확인

---

## I. JWT 유틸리티 구현 (JwtProvider) + Refresh Token 발급·검증

**목적**: Access Token / Refresh Token의 생성·검증·파싱을 담당하는 컴포넌트를 만든다.

**개발 방향**
- jjwt 0.12.x fluent API 기준(`Jwts.builder()...signWith(SecretKey)`, `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`)
- `createAccessToken(User)`: subject=userId, claim `role`, `jwt.access-expiration`(30분) 사용
- **`JwtProvider`는 access token만 담당한다** — refresh token은 JWT가 아닌 불투명 랜덤 문자열이므로(G 항목 참고) `createRefreshToken`을 두지 않는다. 생성은 `SecureRandom` 기반의 별도 컴포넌트(`RefreshTokenGenerator` 또는 `RefreshTokenService` 내부 private 메서드)가 맡는다
- `parseClaims(token)`: `ExpiredJwtException`/`JwtException` 등을 그대로 던져 필터가 종류별로 구분해서 처리하도록 함
- 키는 `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` — HS256이면 `jwt.secret`이 최소 32바이트 이상이어야 함 (구현 시 실제 값 검증 필요)
- `jwt.refresh-expiration`(7일) 설정값은 계속 사용하되, 용도는 **`RefreshToken.expiresAt` 계산과 쿠키 `Max-Age` 전용**이다(JWT `exp` claim이 아님)
- **재발급 흐름**: 쿠키로 받은 **원문을 SHA-256 해시**해 `findByTokenHash`로 조회 → row가 있고 `expiresAt > now()`이면 **신규 access token만 발급**, **미존재·만료 모두** `REFRESH_TOKEN_NOT_FOUND`(U009) 에러
- **rotation을 적용하지 않는다**(G 항목 근거 참고). refresh row와 쿠키는 그대로 두므로 재발급 응답에 `Set-Cookie`가 없고, 7일이 지나면 재로그인한다. 덕분에 여러 탭이 부팅 시 동시에 `/api/auth/refresh`를 호출해도 모두 성공하며, 서버측 grace period나 FE의 단일 Promise 묶기 같은 동시성 장치가 필요 없다
- 탈취 재사용 탐지(reuse detection)와 rotation은 **함께 도입해야 의미가 있으므로** 둘 다 향후 개선 항목으로 미룬다
- 만료된 `refresh_tokens` row 정리(주기적 배치)는 후속 과제 — 로그아웃·탈퇴로 정리되고, 남은 row도 `expiresAt` 검사에서 거절되므로 기능상 무해하다. 우선순위 낮음

**체크포인트**
- [ ] Access Token 만료 시간이 설정값(30분)과 일치하는지 확인
- [ ] Refresh Token 저장/조회 정상 동작
- [ ] 재발급 시 **access token만 새로 발급되고 refresh row·쿠키는 그대로 유지**되는지 확인 (응답에 `Set-Cookie` 없음)
- [ ] 동일 refresh token으로 연속 재발급 시 매번 성공하는지 확인 (탭 여러 개 부팅 시나리오)
- [ ] DB `refresh_tokens.token_hash`에 토큰 원문이 아닌 SHA-256 해시가 저장되는지 확인
- [ ] `expiresAt`이 지난 refresh token으로 재발급 시도 시 U009 반환 확인 (row가 남아 있어도 거절되어야 함)
- [ ] 로그아웃 직후 같은 토큰으로 재발급 시도 시 U009 반환 확인

---

## J. JWT 인증 필터 + CustomUserDetailsService

**목적**: 모든 요청에서 JWT를 추출·검증하여 `SecurityContext`에 인증 정보를 채운다.

**개발 방향**
- `CustomUserDetailsService`(`global/security`): `loadUserByUsername(userId)` → `UserRepository.findById` → **거부 사유를 반드시 구분한다** — 유저 미존재는 `UsernameNotFoundException`, `status==WITHDRAWN`은 `DisabledException`(또는 전용 예외)으로 각각 다르게 던진다. 정상이면 `UserPrincipal`(UserDetails 구현체) 반환. **매 요청마다 DB 조회하는 방식을 채택** — JWT claim만 신뢰하지 않고 탈퇴 등 최신 상태를 즉시 반영하기 위함
- `JwtAuthenticationFilter`(`OncePerRequestFilter`): `Authorization: Bearer ...` 추출 → 없으면 그냥 통과 → 있으면 파싱·`CustomUserDetailsService` 조회 → `SecurityContextHolder`에 `Authentication` 설정 → 예외는 request attribute에만 남기고 필터 체인은 항상 계속 진행(short-circuit 안 함) → 실제 응답은 H 항목의 `JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler`가 이 attribute를 읽어 결정
- **거부 사유 → ErrorCode 매핑** (attribute에 담을 값):

| 상황 | ErrorCode |
|---|---|
| 토큰 없음 / 기타 인증 실패 | `UNAUTHORIZED`(U003, 401) |
| 서명 위조·형식 오류 | `INVALID_TOKEN`(U005, 401) |
| 만료 | `EXPIRED_TOKEN`(U006, 401) |
| 탈퇴 회원(`WITHDRAWN`) | **`WITHDRAWN_USER`(U010, 403)** |

  ⚠️ 탈퇴 사유를 별도 attribute로 넘기지 않으면 U010은 정의만 되고 **절대 반환되지 않는 죽은 코드**가 된다(전부 U003으로 뭉개짐). 또한 U010은 403이므로 **EntryPoint는 401을 하드코딩하지 말고 `errorCode.getHttpStatus()`를 그대로 사용**해야 한다
- 필터 체인 내 위치: `UsernamePasswordAuthenticationFilter` 이전 (H 항목 참고)

**체크포인트**
- [ ] 유효한 토큰으로 인증 정상 통과
- [ ] 토큰 없이 인증 필요 API 요청 시 401 반환
- [ ] 만료/위조 토큰 요청 시 각각 U006/U005로 구분되어 응답되는지 확인
- [ ] 탈퇴(WITHDRAWN) 회원의 아직 유효한 access token으로 요청 시 **U010(403)으로 응답되는지** 확인 (U003으로 뭉개지면 실패 — 거부만 되면 통과가 아니다)

---

## K. 인증 API 구현 (AuthController / UserController) — 카카오 로그인 플로우

**목적**: 카카오 로그인, 토큰 재발급, 로그아웃, 내 정보 CRUD API를 완성한다.

**API 스펙**

| Method | URI | 인증 | 요청 | 응답 data |
|---|---|---|---|---|
| POST | `/api/auth/kakao` | 불필요 | `{code}` | `{accessToken, tokenType, accessTokenExpiresIn, isNewUser}` + `Set-Cookie: refresh_token=...` |
| POST | `/api/auth/refresh` | 불필요 | 바디 없음 (`refresh_token` 쿠키가 자격증명) | `{accessToken, tokenType, accessTokenExpiresIn}` (`Set-Cookie` 없음 — 쿠키 그대로 유지) |
| POST | `/api/auth/logout` | 불필요 | 바디 없음 (`refresh_token` 쿠키) | `ApiResponse.<Void>success(null)` + `Set-Cookie: refresh_token=; Path=/api/auth; Max-Age=0` |
| GET | `/api/users/me` | 필요 | - | `{id, email, name, phone, address, birthDate, role, provider, createdAt}` |
| PUT | `/api/users/me` | 필요 | `{name?, phone?, address?, birthDate?}` (모두 optional) | 위 GET과 동일 |
| DELETE | `/api/users/me` | 필요 | - | `ApiResponse.<Void>success(null)` |

`POST /api/auth/kakao`의 요청 필드는 `code` 하나뿐이다(`@NotBlank`) — `redirectUri`는 서버 설정값으로 고정하므로 받지 않는다(G 항목 참고). 누락 시 기존 `MethodArgumentNotValidException` 핸들러가 400으로 처리하므로 별도 예외 처리를 만들지 않는다.

응답 `data`에 **`refreshToken`을 절대 포함하지 않는다** — 오직 `Set-Cookie`로만 내려야 FE의 JS가 접근할 수 없다. `/api/auth/refresh`·`/logout`은 요청 바디 없이 쿠키만으로 동작하며, 쿠키가 없거나 DB에 해당 row가 없으면 `REFRESH_TOKEN_NOT_FOUND`(U009, 401)로 응답한다.

**Refresh Token 쿠키 속성**

| 속성 | 값 | 이유 |
|---|---|---|
| 이름 | `refresh_token` | |
| `HttpOnly` | ✅ | JS 접근 차단 — XSS로 탈취 불가 |
| `Secure` | ✅ | HTTPS 전송 강제. 브라우저가 `http://localhost`를 secure context로 취급하므로 로컬 개발에서도 정상 동작 |
| `SameSite` | `Strict` | cross-site 요청에 쿠키 미첨부 → CSRF 차단 (H 항목 근거 참고) |
| `Path` | `/api/auth` | `/refresh`·`/logout`에만 전송되고 그 외 API 요청에는 실리지 않아 노출면 축소 |
| `Max-Age` | `jwt.refresh-expiration`(7일)과 동일 | |
| `Domain` | **미설정(host-only)** | 쿠키는 API 호스트가 발급하고 API 호스트로만 전송되며 FE가 읽을 필요가 없다 |

쿠키에 실리는 값은 **불투명 랜덤 문자열 원문(base64url 43자)**이고, DB에는 그 SHA-256 해시만 저장된다(G 항목 참고). 서버는 요청받은 원문을 해시해 조회하므로 원문을 어디에도 보관하지 않는다.

⚠️ **쿠키 삭제 시 `Path`를 반드시 함께 지정한다.** 브라우저는 이름 + 도메인 + `Path`가 **모두 일치**해야 쿠키를 덮어쓰거나 지운다(RFC 6265). 로그아웃·탈퇴 응답에서 `Set-Cookie: refresh_token=; Max-Age=0`만 내리면 Path가 `/`로 해석되어 `Path=/api/auth`로 설정된 쿠키와 매칭되지 않아 **쿠키가 그대로 남는다**. 반드시 `Path=/api/auth`를 포함할 것.

**AuthService.loginWithKakao(code) 흐름**
1. `KakaoOAuthClient.requestToken(code)` → 설정값 `kakao.redirect-uri`를 실어 카카오 access token 발급
2. `KakaoOAuthClient.fetchUserInfo` → kakaoId, email(nullable), nickname
3. `UserRepository.findByProviderAndProviderId(KAKAO, kakaoId)`
   - 있고 ACTIVE → 그대로 사용
   - 있고 WITHDRAWN → `reactivate()` 후 로그인 처리 (동일 카카오 계정 재로그인 시 자동 재활성화)
   - 없음 → 신규 User 생성(email 중복이어도 email은 null 처리 후 계속 진행, 가입 자체를 막지 않음), `isNewUser=true`
4. `JwtProvider`로 access token 발급 + `SecureRandom`으로 refresh token 원문 생성, 원문의 SHA-256 해시를 `RefreshToken` row로 저장(I 항목 참고)
5. access token은 응답 바디에, refresh token **원문**은 위 속성의 `Set-Cookie`로 내림
6. 응답에 `isNewUser` 포함 — FE가 "추가 정보 입력" UX를 선택적으로 띄우는 용도(게이팅 아님, 강제 아님)

**프론트엔드 계약 (토큰 보관)**

이 저장소 범위 밖이지만 백엔드 API 계약의 반대편이므로 함께 명시한다.

1. 인증 관련 모든 요청에 `credentials: 'include'` — 안 붙이면 쿠키가 오가지 않아 refresh가 동작하지 않는다
2. access token은 **JS 변수(메모리)에만** 보관 — `localStorage`/`sessionStorage`/일반 쿠키 저장 금지
3. 새로고침(F5) 시 access token이 소멸하므로, **앱 부팅 시 `POST /api/auth/refresh`를 호출해 세션을 복구**한다(silent refresh). 401이 오면 미로그인 상태로 처리
4. 401 응답 인터셉터: `/api/auth/refresh` 호출 → 성공 시 새 access token으로 원요청 재시도, 실패 시 로그인 화면으로
5. refresh 요청을 단일 Promise로 묶는 것은 **권장(필수 아님)** — rotation을 쓰지 않으므로 동시에 여러 번 호출돼도 모두 성공한다(I 항목). 불필요한 중복 호출을 줄이는 최적화 차원
6. refresh token은 FE가 읽을 수도, 읽을 필요도 없다 — 브라우저가 자동으로 첨부한다

**회원 탈퇴 처리** (`DELETE /api/users/me`)
- 소프트 삭제: `status=WITHDRAWN`, `withdrawnAt` 기록, 해당 유저의 `RefreshToken` 전체 삭제 + `Set-Cookie: refresh_token=; Path=/api/auth; Max-Age=0`으로 쿠키 만료(`Path` 누락 시 쿠키가 지워지지 않는다 — 위 경고 참고)
- 카카오 unlink 호출은 best-effort: 실패해도 로그만 남기고 탈퇴 처리는 계속 진행 (카카오 서버 장애로 탈퇴가 막히는 상황 방지)

**신규 ErrorCode** (`UserErrorCode`, 기존 U001~U006 다음 U007부터 이어서 추가)
```
KAKAO_AUTH_FAILED(400, U007, "카카오 인증에 실패했습니다.")        // 인가코드 문제로 토큰 교환 실패
KAKAO_API_ERROR(502, U008, "카카오 서버 연동 중 오류가 발생했습니다.") // 사용자정보/unlink 호출 실패
REFRESH_TOKEN_NOT_FOUND(401, U009, "유효하지 않거나 만료된 리프레시 토큰입니다.") // 미존재·만료·로그아웃 후 재사용
WITHDRAWN_USER(403, U010, "탈퇴한 회원입니다.")
```
기존 `INVALID_TOKEN`(U005)/`EXPIRED_TOKEN`(U006)은 JWT 자체의 서명/만료 오류에 그대로 재사용.

**설정 값 추가**
- `application.yml`(고정 URL): `kakao.token-uri/user-info-uri/unlink-uri`
- `application-local.yml`/`application-prod.yml`(env 기반, gitignore 대상): `kakao.client-id/client-secret/admin-key/redirect-uri`, `app.cors.allowed-origins`
- 비밀번호 저장 로직은 이번 범위에 없음(OAuth 전용) — LOCAL 로그인 확장 시 문서 하단 "향후 개선 항목" 참고
- 로그인한 사용자 식별은 `@AuthenticationPrincipal`(H/J 항목의 `UserPrincipal`)로 일관되게 사용

**체크포인트**
- [ ] 카카오 로그인(신규 가입) → `GET /api/users/me` 조회까지 전체 플로우 통과
- [ ] 동일 카카오 계정으로 재로그인 시 신규 User가 아닌 기존 User로 인증되는지 확인
- [ ] `code` 누락 시 400 반환 확인 (`@NotBlank` 검증)
- [ ] 만료된 Access Token + 유효한 Refresh Token 쿠키로 재발급 성공
- [ ] 로그아웃 후 이전 Refresh Token 재사용 시 U009 반환 확인
- [ ] 탈퇴 후 동일 카카오 계정 재로그인 시 자동 재활성화(status=ACTIVE) 확인
- [ ] 로그인 응답 바디에 `refreshToken`이 **포함되지 않는지** 확인 (쿠키로만 전달)
- [ ] `Set-Cookie`에 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth`가 모두 붙는지 확인
- [ ] 쿠키 없이 `/api/auth/refresh` 호출 시 U009(401) 반환 확인
- [ ] 로그아웃·탈퇴 응답의 `Set-Cookie`에 `Max-Age=0` **및 `Path=/api/auth`**가 모두 있는지, 그리고 브라우저에서 쿠키가 **실제로 사라지는지** 확인 (Path가 빠지면 `Max-Age=0`이어도 지워지지 않는다)

---

## 향후 개선 항목

이번 Phase 범위에서 제외한 항목들. 조사·설계 근거를 함께 남겨 재도입 시 다시 조사하지 않도록 한다.

**PKCE (RFC 7636, S256)**
- *동기*: `POST /api/auth/kakao`가 permitAll이고 인가코드를 받으면 그 코드 주인의 JWT를 발급하므로, 백엔드 자체가 "인가코드 → 세션" 변환 오라클이 된다. 공격자가 피해자의 인가코드를 얻으면(코드는 URL 쿼리스트링에 실려 Referer·브라우저 히스토리·접근 로그에 남는다) 그대로 POST해 피해자 계정의 JWT를 받는다. `client_secret`도 `redirect_uri` 고정도 이를 막지 못한다 — 공격자 대신 우리 서버가 정상 값을 써주기 때문. OAuth 2.1이 confidential client에도 PKCE를 의무화한 이유가 이 authorization code injection이다.
- *보류 이유*: 유출 경로 대부분이 피해자 브라우저·네트워크 접근을 전제하는데 그 수준이면 access token도 함께 노출된다. 인가코드는 단발성 + 약 10분 만료라 창도 좁다. 반면 비용은 FE의 Web Crypto 구현 + sessionStorage 왕복 계약, 그리고 아래 미검증 리스크다.
- *도입 전 필수 검증*: 카카오 discovery(`https://kauth.kakao.com/.well-known/openid-configuration`)는 `"code_challenge_methods_supported": ["S256"]`을 명시하나(`plain` 미지원), **한글 REST API 문서에는 `code_challenge`/`code_verifier`가 없다**. 브라우저로 `/oauth/authorize?...&code_challenge={S256값}&code_challenge_method=S256` 접속 → `curl`로 `/oauth/token`에 `code_verifier` 포함해 교환 성공을 먼저 확인할 것.

**Refresh Token Rotation + 재사용 탐지** — 둘은 **함께** 도입해야 의미가 있다(탐지 없는 rotation은 복잡도만 남긴다, I 항목). 도입 시 멀티탭 경합 대응이 반드시 따라온다: 재발급할 때 기존 row를 즉시 삭제하지 말고 `expiresAt`을 `now()+30초`로 단축하는 서버측 grace period, 그리고 FE의 refresh 단일 Promise 묶기. 로그아웃·탈퇴는 grace 없이 즉시 하드 삭제해야 한다.

**LOCAL 로그인(이메일 + 비밀번호)** — `V2__add_password.sql`로 `password` 컬럼(nullable) 추가 + `PasswordEncoder`(BCrypt) Bean 등록 + `Provider.LOCAL` 상수 추가 + 이메일 중복 검사(`existsByEmail`, U001). 지금 컬럼과 Bean을 미리 만들지 않는 이유는 Flyway 이력에 쓰지 않을 스키마가 영구히 남기 때문이다.

**만료 `refresh_tokens` row 정리 배치** — 기능상 무해하므로(만료 검사에서 거절됨) 테이블이 커질 때 도입.

**ADMIN 계정 발급 방식** — 미해결. [[phase6-finalization]] R 항목에서 RBAC 최종 점검 시 함께 결정.

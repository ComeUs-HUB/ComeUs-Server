# user / auth — 회원 · 인증

카카오 소셜 로그인, JWT 발급·검증, 회원 프로필과 생명주기를 담당한다.
**왜 이렇게 정했는가**는 [decisions/](../decisions/)에 있다. 이 문서는 *무엇이 계약인가*만 적는다.

## 도메인 경계

| 도메인 | 소유 |
|---|---|
| `domain/user` | `User`·`Role`·`UserStatus`·`Provider`, `UserRepository`, `UserService`, `UserController`, `UserErrorCode` |
| `domain/auth` | `RefreshToken`, `KakaoOAuthClient`, `JwtProvider`·`SecureTokenGenerator`, `JwtProperties`·`KakaoProperties`·`RestClientConfig`, `AuthService`, `AuthController`, `AuthErrorCode` |
| `global/security` | Spring Security 어댑터만 — `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`, `CustomUserDetailsService`, `UserPrincipal` |

깨뜨리면 안 되는 규칙:

- **서비스 계층의 의존 방향은 `auth → user` 단방향.** `UserService`는 `AuthService`를 부르지 않는다 —
  탈퇴만 예외이며 그 호출은 `UserController`가 소유한다(아래)
- **auth는 `UserRepository`를 직접 주입하지 않는다** — `UserService`의 공개 메서드만 경유한다.
  회원 생성 규칙이 한 곳(user)에 남게 하기 위함이다
- `Provider` enum은 `User.provider`의 타입이므로 **user에 남긴다**
- `AppProperties`는 `SecurityConfig`(CORS)와 `AuthController`(FE 복귀 주소)가 함께 쓰므로
  `global/config`에 둔다
- 탈퇴만 두 도메인을 함께 건드리는데, **순서를 `UserController`가 소유**해 순환 의존을 막는다

## 엔티티

### `User` — `domain/user/entity/User.java`

| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK, IDENTITY | |
| email | String | nullable, unique | 카카오 이메일 동의 거부 시 null |
| name | String | nullable | 카카오 닉네임으로 초기화 |
| phone / address / birthDate | String / String / LocalDate | nullable | `PUT /users/me`로 추후 입력 (강제 아님) |
| role | `Role`(USER/ADMIN) | not null, default USER | ADMIN 발급 플로우는 범위 밖 |
| provider | `Provider`(KAKAO) | not null | |
| providerId | String | not null | 카카오 고유 사용자 id |
| status | `UserStatus`(ACTIVE/WITHDRAWN) | not null, default ACTIVE | 탈퇴는 소프트 삭제 |
| withdrawnAt | LocalDateTime | nullable | |
| createdAt / updatedAt | LocalDateTime | `@CreatedDate` / `@LastModifiedDate` | `@EntityListeners(AuditingEntityListener.class)` |

- 유니크: `email` 단일, **`(provider, providerId)` 복합**(중복 가입 방지)
- Setter 없이 도메인 메서드로만 상태를 바꾼다 — `ofKakao`, `updateProfile`, `withdraw`,
  `reactivate`, `isWithdrawn`
- 테이블명은 **`users`** — `USER`는 표준 SQL·PostgreSQL에서 예약어라 이식성을 위해 피한다

### `RefreshToken` — `domain/auth/entity/RefreshToken.java`

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | PK |
| user | User | `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name="user_id", nullable=false)` |
| tokenHash | `CHAR(64)` | 원문의 SHA-256 hex, unique |
| expiresAt | LocalDateTime | 만료 판정의 **유일한** 근거 |
| createdAt | LocalDateTime | `@CreatedDate` |

`user 1 : token N`이고 DB엔 해시만 저장한다 — 근거는
[ADR 0005](../decisions/auth/0005-opaque-refresh-token-hashed-in-db.md).
로그아웃은 해당 row만, 탈퇴는 해당 유저 전체 row를 삭제한다.

## Repository

- `UserRepository`(user): `findByProviderAndProviderId`, `existsByEmail`
  - **user 패키지 밖에서 주입하지 않는다**
- `RefreshTokenRepository`(auth)
  - `findByTokenHash` — `@EntityGraph(attributePaths = "user")` (재발급 시 User가 항상 필요)
  - `deleteByTokenHash`, `deleteAllByUserId` — 둘 다 `@Modifying @Query` 벌크 삭제
    - `deleteAllByUserId`는 `@ManyToOne User user`의 속성 경로로 파생된다. `User` 엔티티를 넘기지 않으므로
      **탈퇴 시 auth가 회원을 로딩할 필요가 없다**
    - `@Modifying(clearAutomatically = true)`만 붙인다. 탈퇴 흐름에서 `user.withdraw()`와 이 삭제는
      **서로 다른 트랜잭션**이라 flush 순서가 얽히지 않는다(`flushAutomatically` 불필요)

## 경계 메서드

- `UserService.findOrCreateKakaoUser(providerId, email, nickname)` → `KakaoUserResolution(User, isNewUser)`
  - 조회 → ACTIVE 재사용 / WITHDRAWN `reactivate()` / 없으면 신규 생성(email 중복이면 email만 null)
  - **이 판단 전체를 user가 소유한다.** auth는 카카오에서 받은 값만 넘긴다
  - 재활성화는 기존 회원이므로 `isNewUser=false`
- `UserService.withdraw(userId)` → `String providerId` (소프트 삭제만 수행)
- `AuthService.revokeAllTokens(userId)` / `AuthService.unlinkKakao(providerId)`

**가입 경합**은 `AuthService`가 `DataIntegrityViolationException`을 잡아 로그인 DB 구간을 **1회 재시도**해
흡수한다. ⚠️ 카카오 왕복이 트랜잭션 밖에 있어야 재시도가 1회용 인가 코드를 재사용하지 않는다 —
[ADR 0009](../decisions/auth/0009-signup-race-retry-at-login-boundary.md).

## API

| Method | URI | 인증 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/auth/kakao/authorize` | 불필요 | - | **302** → 카카오 + `Set-Cookie: oauth_state` |
| GET | `/api/auth/kakao/callback` | 불필요 | `?code=&state=` (카카오가 채움) | **302** → FE + `Set-Cookie: refresh_token` |
| POST | `/api/auth/refresh` | 불필요 (쿠키가 자격증명) | 없음 | `{accessToken, tokenType, accessTokenExpiresIn}` — **`Set-Cookie` 없음** |
| POST | `/api/auth/logout` | 불필요 | 없음 | `null` + 쿠키 만료 `Set-Cookie` |
| GET | `/api/users/me` | 필요 | - | `{id, email, name, phone, address, birthDate, role, provider, createdAt}` |
| PUT | `/api/users/me` | 필요 | `{name?, phone?, address?, birthDate?}` 전부 optional | 위 GET과 동일 |
| DELETE | `/api/users/me` | 필요 | - | `null` |

- **응답 `data`에 `refreshToken`을 절대 포함하지 않는다** — 오직 `Set-Cookie`로만
- `/logout`이 permitAll인 이유: access token이 만료된 상태에서도 로그아웃할 수 있어야 한다
- 로그인 사용자 식별은 `@AuthenticationPrincipal`(`UserPrincipal`)로 일관되게

## 쿠키 2종

둘 다 `HttpOnly` + `Domain` 미설정(host-only)이고, `Secure`는 `app.cookie.secure`(기본 `true`)를 따른다.
`global/util/CookieUtil`이 **두 쿠키의 속성을 만드는 유일한 지점**이다.

| | `refresh_token` | `oauth_state` |
|---|---|---|
| 값 | 불투명 랜덤 원문 (base64url 43자) | 랜덤 state (base64url) |
| `SameSite` | `Strict` | **`Lax`** |
| `Path` | `/api/auth` | **`/api/auth/kakao/callback`** |
| `Max-Age` | 7일 (`jwt.refresh-expiration`) | **5분** |
| 수명 | 로그아웃·탈퇴까지 | 콜백에서 1회 사용 후 즉시 삭제 |

⚠️ `SameSite`를 둘 다 `Strict`로 맞추면 **로그인이 전부 실패한다.** 삭제할 때도 발급할 때와 **같은 `Path`를
반드시 지정**해야 쿠키가 지워진다. 근거와 함정 전체는
[ADR 0006](../decisions/auth/0006-cookie-samesite-and-path.md).

## 로그인 흐름

```
1. Client → BE     GET /api/auth/kakao/authorize
2. BE → Client     302 → kauth.kakao.com/oauth/authorize   [Set-Cookie: oauth_state]
3. Client ↔ KAKAO  로그인·동의 (브라우저가 카카오 페이지에서 직접 처리)
4. KAKAO → BE      302 → GET /api/auth/kakao/callback?code=&state=
                        (Client가 아니라 BE로 직행. 브라우저가 oauth_state 쿠키 동봉)
5. BE              state 대조 → code 교환 → 사용자정보 → User 조회/생성 [Set-Cookie: refresh_token]
6. BE → Client     302 → {app.frontend.base-url}/oauth/complete
7. Client → BE     POST /api/auth/refresh  → accessToken 획득 (silent refresh)
```

**`/authorize`** — `SecureRandom`으로 state 생성 → `oauth_state` 쿠키 설정 → 카카오 authorize URL로 302.
쿼리: `client_id`, `redirect_uri`(설정값), `response_type=code`, `state`

**`/callback` 처리 순서**

1. 카카오가 `?error=`를 실어 보낸 경우(동의 거부 등) → 그대로 FE로 302 `?error=`
2. `oauth_state` 쿠키와 쿼리 `state` 대조 → 불일치·누락이면 A009
3. **`oauth_state` 쿠키 즉시 삭제** (1회용)
4. `requestToken(code)` → `fetchUserInfo` → kakaoId, email(nullable), nickname
5. `userService.findOrCreateKakaoUser(...)` → `KakaoUserResolution(User, isNewUser)`
6. refresh 원문 생성 → 해시를 `RefreshToken` row로 저장 → 원문을 `refresh_token` 쿠키로 설정
7. `{app.frontend.base-url}/oauth/complete`로 302. 신규 가입이면 `?isNewUser=true`
   (자격증명이 아니라 "추가 정보 입력" UX 용도라 query param으로 안전. 게이팅 아님)

**access token은 이 흐름에서 발급하지 않는다** — 7번의 `/api/auth/refresh`가 발급한다.
그 결과 access token이 URL에 실리는 경로가 설계상 존재하지 않는다 —
[ADR 0002](../decisions/auth/0002-backend-owns-redirect-uri.md).

## 토큰

`domain/auth/token/JwtProvider`, `domain/auth/token/SecureTokenGenerator`

- jjwt 0.12.x. 키는 `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` — `jwt.secret` **최소 32바이트**
  - ⚠️ **서명 알고리즘은 키 길이가 정한다**: 32바이트↑ HS256 / 48↑ HS384 / 64↑ HS512. 코드로 지정하지 않는다
- `createAccessToken(User)`: subject=userId, claim `role`, `jwt.access-expiration`(30분)
- `parseClaims(token)`: `ExpiredJwtException`/`JwtException`을 **그대로 던져** 필터가 종류별로 구분하게 한다
- **`JwtProvider`는 access token만 담당** — refresh token 생성은 `SecureTokenGenerator`가 맡는다
- **재발급**: 쿠키 원문을 SHA-256 해시 → `findByTokenHash` → row 존재 && 미만료면 **신규 access token만**
  발급. 미존재·만료 모두 A007

## 인증 필터

- `CustomUserDetailsService`: `loadUserByUsername(userId)` → `findById` → `UserPrincipal`
  - **매 요청 DB 조회** — JWT claim만 믿지 않고 탈퇴 등 최신 상태를 즉시 반영한다
  - ⚠️ **거부 사유를 구분해서 던진다**: 미존재 `UsernameNotFoundException` / WITHDRAWN `DisabledException`
- `JwtAuthenticationFilter`(`OncePerRequestFilter`): `Bearer` 토큰 없으면 그냥 통과. 있으면
  파싱 → 조회 → `SecurityContextHolder` 설정
  - **예외 시 short-circuit 하지 않는다.** request attribute에 사유만 남기고 체인을 계속 진행한다

401 응답은 `JwtAuthenticationEntryPoint`가, 인가 실패 403은 `JwtAccessDeniedHandler`가 쓴다 —
[ADR 0010](../decisions/platform/0010-access-denied-handler-for-authorization.md).
⚠️ **익명 요청은 권한 부족이어도 EntryPoint로 간다**(필터가 `SecurityContext`를 비우기 때문).
그래서 auth·user 엔드포인트의 A001·A003·A004·A008 동작은 Phase 3 이후에도 그대로다.

인증 통과 후 상태가 바뀐 경합에서는 `UserService`가 **필터와 같은 규칙**으로 사유를 구분한다 —
row 없음 A001(401), 탈퇴 A008(403). 미존재를 404로 내리지 않는 이유는 이 API가 회원을 지목하지 않기
때문이다(경로에 `{userId}`가 없다). "없는 리소스"가 아니라 "무효해진 세션"이라 FE의 401 인터셉터가
흡수해야 한다.

## 회원 탈퇴 (`DELETE /api/users/me`)

user와 auth 양쪽을 건드리는 **유일한 흐름**이다. 순서를 `UserController`가 소유해
`UserService`가 `AuthService`를 참조하지 않게 한다.

```
1. authService.revokeAllTokens(userId)                # RefreshToken 전수 삭제
2. String providerId = userService.withdraw(userId)   # 소프트 삭제 + providerId 획득
3. authService.unlinkKakao(providerId)                # 카카오 연결 해제 (best-effort)
4. refresh_token 쿠키 만료 (Path=/api/auth 필수)
```

- 1은 **fail-closed** — 실패하면 예외가 전파되어 탈퇴가 중단되고 회원은 ACTIVE로 남는다(재로그인으로 복구)
- 3은 **best-effort** — 실패해도 로그만 남기고 탈퇴는 성립한다(카카오 장애로 탈퇴가 막히면 안 된다).
  예외를 삼키는 판단은 호출자가 아니라 `AuthService` 안에서 한다
- ⚠️ **순서를 뒤집지 말 것.** 2를 먼저 하면 WITHDRAWN인데 `refresh_tokens` row가 남는 상태가 생긴다
- 1과 2는 별도 트랜잭션이라 원자적이지 않다. 어느 쪽이 실패해도 결과가 fail-safe하고, 카카오 `unlink`는
  외부 호출이라 애초에 트랜잭션으로 묶을 수 없다. 원자성이 필요해지면 도메인 이벤트로 전환한다

## 카카오 연동

- `KakaoProperties`(`prefix="kakao"`): `clientId, clientSecret, adminKey, redirectUri,
  authorizeUri, tokenUri, userInfoUri, unlinkUri`
  - **`redirectUri`는 BE 콜백 주소**(`{api-host}/api/auth/kakao/callback`)다. FE 주소가 아니다
- `RestClientConfig`: kauth/kapi용 `RestClient` Bean 2개 (신규 의존성 불필요 — `RestClient`는
  `starter-web`에 포함. `starter-oauth2-client`·webflux 사용 안 함)
- `KakaoOAuthClient`: `requestToken(code)`, `fetchUserInfo(kakaoAccessToken)`, `unlink(providerId)`
  - **카카오 호출 실패는 이 경계에서 `BusinessException`으로 변환**한다 — 상위 계층이 카카오의 존재를 모르게 한다
  - 4xx → A005, 5xx → A006
  - 토큰 요청 form: `grant_type=authorization_code`, `client_id`, `client_secret`, `redirect_uri`, `code`
- DTO는 record + `@JsonNaming(SnakeCaseStrategy)`

| 용도 | 엔드포인트 |
|---|---|
| 인가 | `GET https://kauth.kakao.com/oauth/authorize` |
| 토큰 | `POST https://kauth.kakao.com/oauth/token` |
| 사용자 정보 | `GET https://kapi.kakao.com/v2/user/me` |
| 연결 해제 | `POST https://kapi.kakao.com/v1/user/unlink` (Admin Key) |

⚠️ **카카오 개발자 콘솔에 Redirect URI 등록이 선행 조건이다.** local·prod 각각.
누락하면 카카오가 `KOE006`으로 거부한다.

## 설정 값

- `application.yml`(고정 URL, 커밋됨): `kakao.authorize-uri`, `token-uri`, `user-info-uri`, `unlink-uri`
- `application-local.yml` / `application-prod.yml`(env 기반, **gitignore**):
  `kakao.client-id`, `client-secret`, `admin-key`, `redirect-uri`,
  `app.frontend.base-url`, `app.cors.allowed-origins`, `app.cookie.secure`
  - `kakao.redirect-uri` — **BE 콜백** (local: `http://localhost:8080/api/auth/kakao/callback`)
  - `app.frontend.base-url` — 로그인 완료 후 복귀 대상 (local: `http://localhost:3000`)
  - `app.cookie.secure` — local `false` / prod `true`

로컬에서도 성립한다: `localhost:3000`(FE) ↔ `localhost:8080`(BE)은 포트가 달라도 **same-site**라
`SameSite=Strict`인 `refresh_token`이 정상 전송된다.

## FE 계약

저장소 범위 밖이지만 API 계약의 반대편이라 여기 적는다.
FE는 **인가 코드를 다루지 않는다.** authorize URL 조립도, 콜백 `code` 추출도 FE 몫이 아니다.

1. 로그인 버튼 → `location.href = '{API_BASE}/api/auth/kakao/authorize'` — XHR이 아니라 **페이지 이동**이어야
   한다 (fetch로 호출하면 302를 따라가 버려 카카오 로그인 화면이 뜨지 않는다)
2. `/oauth/complete` 페이지를 만들고, 거기서 `POST /api/auth/refresh`를 호출해 accessToken을 얻는다.
   `?error=`가 있으면 에러 UI, `?isNewUser=true`면 추가 정보 입력 UX
3. 인증 요청에 `credentials: 'include'` (없으면 쿠키가 오가지 않는다)
4. access token은 **JS 변수(메모리)에만** 보관. `localStorage`/`sessionStorage`에 어떤 토큰도 저장하지 않는다
5. 새로고침 시 access token이 소멸하므로 **앱 부팅 시 `/api/auth/refresh`로 세션 복구**(silent refresh).
   401이면 미로그인 처리 — 2번과 동일한 호출이라 로직을 공유할 수 있다
6. 401 인터셉터: refresh → 성공 시 원요청 재시도, 실패 시 로그인 화면
7. refresh 단일 Promise 묶기는 **권장(필수 아님)** — rotation이 없어 동시 호출도 모두 성공한다
   ([ADR 0004](../decisions/auth/0004-no-refresh-token-rotation.md))

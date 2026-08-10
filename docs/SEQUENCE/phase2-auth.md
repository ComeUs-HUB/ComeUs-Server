# Phase 2 — 인증/인가 (user/auth 도메인, G~K)

> 상태: 미구현. `domain/user`, `domain/auth`, `global/security` 패키지에 **본문이 빈 프로토타입 골격**만 존재한다.
> 선반영됨: `UserErrorCode`(U001), `AuthErrorCode`(A001~A009), `application.yml`의 `jwt.*`,
> `JpaConfig`의 `@EnableJpaAuditing`, `BackendApplication`의 `@ConfigurationPropertiesScan`.

이후 모든 도메인이 "로그인한 사용자"에 의존하므로 가장 먼저 진행한다.

---

## 도메인 분리 (user / auth)

인증 책임을 `domain/auth`로 분리했다. 아래 규칙을 구현 중 깨지 않도록 한다.

| 도메인 | 소유 |
|---|---|
| `domain/user` | `User`·`Role`·`UserStatus`·`Provider`, `UserRepository`, `UserService`, `UserController`, `UserErrorCode` |
| `domain/auth` | `RefreshToken`, `KakaoOAuthClient`, `JwtProvider`·`SecureTokenGenerator`, `JwtProperties`·`KakaoProperties`·`RestClientConfig`, `AuthService`, `AuthController`, `AuthErrorCode` |
| `global/security` | Spring Security 어댑터만 — `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`, `CustomUserDetailsService`, `UserPrincipal` |

- **의존 방향은 `auth → user` 단방향.** user는 auth를 참조하지 않는다
- **auth는 `UserRepository`를 직접 주입하지 않는다** — `UserService`의 공개 메서드만 경유한다. 회원 생성 규칙이 한 곳(user)에 남게 하기 위함
- `Provider` enum은 `User.provider`의 타입이므로 **user에 남긴다**
- `AppProperties`는 `SecurityConfig`(CORS)와 `AuthController`(FE 복귀 주소)가 함께 쓰므로 `global/config`에 남긴다
- 탈퇴만 두 도메인을 함께 건드리는데, 순서를 `UserController`가 소유해 순환 의존을 막는다 (K 항목 참고)

---

## 구현 순서 (1~9단계)

아래 G~K 항목이 **설계**라면 이 절은 **순서와 완료 조건**이다. 각 클래스의 Javadoc 첫 줄에 `[N단계 · G]` 형태로 소속 단계가 표기돼 있다.

### 순서를 정한 근거

**1. 의존 그래프의 위상 정렬 — 하한선**
컴파일조차 안 되는 순서는 애초에 배제된다. 다만 이것만으로 순서가 유일하게 결정되지는 않는다(카카오 클라이언트와 JWT 부품은 서로 독립이라 어느 쪽이 먼저여도 컴파일된다). 나머지는 아래 원칙이 결정한다.

**2. 각 단계는 그 단계에서 검증이 끝나야 한다 — 가장 중요한 기준**
"다음 단계를 해야 확인 가능"한 배치를 피한다. 이 원칙이 6단계(Security)를 7단계(서비스)보다 **앞에** 두는 이유다. 인증의 *거절* 경로(401/403)는 토큰 발급 없이도 전부 검증된다 — 테스트에서 `JwtProvider`로 토큰을 직접 만들어 넣으면 된다. 순서를 뒤집으면 6단계 검증이 8단계까지 밀린다.

**3. 되돌리기 비용이 큰 결정을 앞으로**
Flyway 전환이 여기 해당한다. `ddl-auto: update` 위에서 Repository를 검증해두면, 나중에 `validate`로 바꾸는 순간 **이미 통과했던 테스트가 다시 깨진다** — Hibernate가 발명한 스키마와 손으로 쓴 DDL이 다르기 때문이다. 그래서 스키마를 엔티티 직후(2단계)에 확정하고, `update`에는 한 번도 의존하지 않는다. 테이블이 2개일 때가 전환 비용도 최소다.

**4. 외부 의존을 앞으로**
카카오 개발자 콘솔의 Redirect URI 등록은 코드가 아니라 외부 작업이고, 누락되면 카카오가 `KOE006`으로 거부해 **8단계가 통째로 막힌다**. 4단계에서 카카오 연동을 하면 이 준비물이 그 시점에 드러난다.

**5. 마지막은 실패 지점을 좁히는 방향으로 쪼갠다**
8·9단계 분리가 여기 해당한다. OAuth 왕복·쿠키 속성·리다이렉트·토큰 발급이 한 단계에 몰리면 브라우저에서 처음 실패했을 때 원인 후보가 너무 많다. 8단계는 "로그인이 되는가", 9단계는 "그 세션을 쓸 수 있는가"만 본다.

### 단계 표

완료 조건은 "컴파일된다"가 아니라 아래 검증이 통과하는 것이다.

| 단계 | 항목 | 파일 | 완료 조건 |
|---|---|---|---|
| 0 | 글로벌 기반 ✅ | — | `*ErrorCode`, `ApiResponse`, `GlobalExceptionHandler`, `JpaConfig`, `@ConfigurationPropertiesScan` |
| 1 · G | 엔티티 ✅ | 5 | 컴파일. 실행 검증은 2단계에서 함께 한다 |
| 2 · G | **Flyway + 스키마** ✅ | 0 (SQL·yml) | 앱 기동 시 Flyway가 두 테이블 생성 + `validate` 통과 |
| 3 · G | Repository | 2 | `@DataJpaTest` — 조회·벌크 삭제·`(provider, providerId)` 중복 차단 |
| 4 · G | 카카오 연동 | 5 | `MockRestServiceServer`로 4xx→A005, 5xx→A006 변환 |
| 5 · I | 토큰 부품 | 3 | 단위 테스트 — 생성·파싱 왕복, 만료/위조 예외 구분, 해시 64자 |
| 6 · H·J | Security 배선 | 6 | 미인증 401(A001), 위조 A003 / 만료 A004 / 탈퇴 **A008(403)** 구분 |
| 7 · I·K | 서비스 | 3 | `findOrCreateKakaoUser` 3분기, 재발급 유효성, `logout` 멱등 |
| 8 · K | 로그인 API | 4 | **브라우저에서** 카카오 로그인 → 쿠키 심김 → `/refresh`가 accessToken 반환 |
| 9 · K | 회원 API | 3 | 그 accessToken으로 `/api/users/me` GET·PUT·DELETE, 탈퇴 후 재로그인 재활성화 |

단계별 상세 설계는 G~K 항목을 참고한다. 파일 합계 31개(2단계는 Java 파일 없음).

### 단계별 배치 근거

- **1 → 2**: 엔티티 매핑과 DDL을 즉시 대조한다. `validate`가 1단계 결과물의 첫 실행 검증 역할을 한다. `@Enumerated(STRING)`이 네이티브 `enum` 컬럼을 만드는 함정이 여기서 터진다
- **2 → 3**: Repository 테스트가 Hibernate가 발명한 스키마가 아니라 확정된 스키마 위에서 돈다
- **4가 7보다 앞**: 카카오 클라이언트는 외부 경계라 나머지와 독립적으로 검증된다. `AuthService` 조립 시점에는 이미 검증된 부품이어야 한다
- **5가 6보다 앞**: 필터가 `JwtProvider`를 요구한다
- **6이 7보다 앞**: 위 원칙 2. 특히 **A008이 죽은 코드가 아닌지를 컨트롤러 없이 검증할 수 있다** — `CustomUserDetailsService`가 `UsernameNotFoundException`/`DisabledException`을 구분해 던지지 않으면 전부 A001로 뭉개지는데, 그걸 이 단계에서 잡는다
- **8 → 9**: 6단계에서 검증한 건 "거절"이고, 9단계에서 비로소 "통과"가 검증된다. 탈퇴 오케스트레이션(`auth → user → auth`)도 두 도메인이 모두 완성된 이 시점에만 가능하다

### ⚠️ 단계별 설정 함정

각 단계에서 함께 처리하지 않으면 그 자리에서 빌드가 깨지는 항목들이다.

| 단계 | 함정 | 조치 |
|---|---|---|
| 2 | 테스트는 H2(`MODE=MySQL`)라 MySQL DDL을 못 돌린다 | `src/test/resources/application.yml`에 `spring.flyway.enabled: false` 추가. 없으면 `contextLoads`가 깨진다 |
| 3 | `@DataJpaTest`는 H2에서 **엔티티 매핑으로부터** 스키마를 만든다 | 유니크 제약을 `V1__init.sql`에만 쓰면 테스트에서 재현되지 않아 중복 가입 테스트가 통과해버린다. `@Table(uniqueConstraints = ...)`로 **엔티티에도** 선언 |
| 5 | `JwtProperties`가 실제 빈이 되는 순간 `jwt.*`가 없어 컨텍스트 로딩이 깨진다 | 테스트 yml은 main `application.yml`을 대체(shadow)한다. `jwt.secret`(HS256이라 32바이트 이상) 더미 값 추가. 지금 통과하는 건 아직 아무도 `${JWT_SECRET}`을 바인딩하지 않기 때문 |
| 4·8 | `kakao.*`·`app.*` 설정이 `application-local.yml`(gitignore)에 있다 | 새 환경에서 클론하면 이 파일이 없다. 필요한 키 목록은 아래 "설정 값" 절 참고 |

---

## 확정 사항 (임의로 되돌리지 말 것)

일반적인 "모범 사례"와 다르게 결정한 항목들이다. 각 결정에는 이유가 있으니 구현 중 임의로 추가하지 말고, 바꿔야 한다면 먼저 문의할 것.

| 결정 | 이유 |
|---|---|
| **카카오 OAuth 전용** — 이메일+비밀번호 로그인/회원가입 없음 | 범위 축소. `password` 컬럼·`PasswordEncoder` Bean 모두 **만들지 않는다** |
| **PKCE 미적용** — `code_challenge`/`code_verifier`를 보내지 않음 | 카카오 REST API 문서에 명시되지 않은 미검증 기능. 단 BE가 redirect를 소유하게 되어 도입 비용은 크게 낮아졌다(향후 개선 항목 참고) |
| **Refresh Token Rotation 미적용** | 재사용 탐지와 함께 도입해야 실익이 있는데 탐지가 범위 밖. 단독 rotation은 멀티탭 경합 대응 복잡도만 남긴다 |
| **Refresh Token은 JWT가 아닌 불투명 랜덤 문자열** | DB를 조회해 유효성을 판정하므로 claim을 파싱하는 곳이 없다. 서명·`exp`가 전부 미사용 |
| **`redirect_uri`는 BE 콜백(`/api/auth/kakao/callback`)을 가리킨다** — FE는 인가코드를 다루지 않는다 | OAuth Authorization Code Grant는 `redirect_uri`를 confidential client(백엔드)가 소유하는 것을 전제한다. 인가코드가 FE의 JS 컨텍스트에 도달하지 않는다 |
| **`state` 필수** — `oauth_state` 쿠키는 `SameSite=Lax` | BE가 인가 요청을 시작하므로 CSRF 방어에 필요. 콜백이 cross-site 네비게이션이라 `Strict`면 쿠키가 안 실려 **로그인이 전부 실패한다** |
| **Access Token은 응답 바디, Refresh Token은 `HttpOnly` 쿠키** | `localStorage`/`sessionStorage`에 어떤 토큰도 저장하지 않는다. **access token을 URL·쿼리스트링에 싣지 않는다** |
| **배포 전제: FE/BE 동일 상위 도메인** (`app.example.com` / `api.example.com`) | 쿠키가 `SameSite=Strict`라 별도 사이트로 분리하면 **refresh/logout이 동작 불능**([[phase7-deployment]] X 항목) |

---

## G. 엔티티 + Flyway + Repository + 카카오 연동

> **1단계 엔티티 → 2단계 Flyway → 3단계 Repository → 4단계 카카오 연동** 순으로 서술한다.
> Flyway가 Repository보다 앞서는 근거는 위 "구현 순서" 절 원칙 3.
> ("user / auth 경계 메서드"만 7단계 항목인데, Repository 사용 규칙과 붙어 있어야 읽히므로 여기 둔다.)

### User (1단계 · `domain/user/entity/User.java`)

| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK, IDENTITY | |
| email | String | nullable, unique | 카카오 이메일 동의 거부 시 null |
| name | String | nullable | 카카오 닉네임으로 초기화 |
| phone / address / birthDate | String / String / LocalDate | nullable | `PUT /users/me`로 추후 입력 (강제 아님) |
| role | Role(USER/ADMIN) | not null, default USER | ADMIN 발급 플로우는 범위 밖 |
| provider | Provider(KAKAO만 정의) | not null | |
| providerId | String | not null | 카카오 고유 사용자 id |
| status | UserStatus(ACTIVE/WITHDRAWN) | not null, default ACTIVE | 탈퇴는 소프트 삭제 |
| withdrawnAt | LocalDateTime | nullable | |
| createdAt / updatedAt | LocalDateTime | `@CreatedDate` / `@LastModifiedDate` | `@EntityListeners(AuditingEntityListener.class)` 추가 |

- 유니크: `email` 단일, `(provider, providerId)` 복합(중복 가입 방지)
- Setter 없이 `ofKakao(providerId, email, name)`, `updateProfile(name, phone, address, birthDate)`, `withdraw()`, `reactivate()`, `isWithdrawn()` 도메인 메서드로만 상태 변경
- 테이블명은 `users` — `USER`는 표준 SQL·PostgreSQL 등에서 예약어라 이식성을 위해 피한다 (MySQL에선 비예약 키워드)
- `Provider`/`Role`/`UserStatus`는 별도 enum 파일

### RefreshToken (1단계 · `domain/auth/entity/RefreshToken.java`)

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | PK |
| user | User | `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name="user_id", nullable=false)` |
| tokenHash | `CHAR(64)` | 토큰 원문의 SHA-256 hex, unique |
| expiresAt | LocalDateTime | 만료 판정의 **유일한** 근거 |
| createdAt | LocalDateTime | `@CreatedDate` |

- **`user 1 : token N`** — 로그아웃이 해당 기기만 무효화하므로 멀티 디바이스 동시 로그인이 전제다. `@OneToOne` 금지
- **DB엔 해시만 저장**: 원문은 `Set-Cookie`로만 나간다. 서버는 요청받은 원문을 해시해 조회하므로 원문을 보관하지 않는다
- **유효성 = row 존재 AND `expiresAt > now()`** — row 존재만 보면 만료 토큰이 영구히 유효해진다
- 로그아웃: 해당 row만 삭제 / 탈퇴: 해당 유저 전체 row 삭제

### Flyway 도입 (2단계)

- `build.gradle.kts`에 `flyway-core`, `flyway-mysql` 추가
- `application.yml`: `spring.flyway.enabled: true`, `locations: classpath:db/migration`
- **`ddl-auto`를 local/prod 모두 `validate`로 통일** — `application-prod.yml`은 이미 `validate`, `application-local.yml`은 미지정이라 `application.yml`의 `update`만 고치면 된다
- `db/migration/V1__init.sql`에 `users`, `refresh_tokens` DDL을 위 엔티티 표와 1:1로 작성
  - 1단계 엔티티가 H2에서 생성하는 DDL을 기준으로 삼는다: enum 3종은 `varchar(20)`, `token_hash`는 `char(64)`, 유니크는 `uk_users_email`·`uk_users_provider_provider_id`·`uk_refresh_tokens_token_hash`
  - Hibernate 6이 enum 컬럼에 `check (role in ('USER','ADMIN'))` 같은 제약을 자동 생성하는데, **`V1__init.sql`에는 옮기지 않기로 했다**. `validate`가 check 제약을 검사하지 않아 기동에 영향이 없고, 넣으면 enum 상수를 하나 추가할 때마다 마이그레이션이 강제된다(`Provider.GOOGLE` 추가 등). 이 컬럼에 쓰는 주체는 Java enum을 다루는 JPA뿐이라 무효값이 들어올 경로가 없다. 결과적으로 H2 테스트에만 있는 제약이 되지만 무해하다
- 이후 Phase에서는 `V2__`, `V3__`… 추가만 하고 기존 파일은 수정하지 않는다
- ⚠️ `src/test/resources/application.yml`에 `spring.flyway.enabled: false` 추가 — 테스트는 H2라 MySQL DDL을 못 돌린다

**검증** — 실행 수단은 무관하다. IDE 실행이든 `./gradlew bootRun`이든 **앱이 MySQL에 기동되기만 하면** 된다.
`flyway.enabled: true` + `ddl-auto: validate` 상태에서 컨텍스트가 뜬다는 것 자체가
"Flyway가 마이그레이션을 적용했고 엔티티↔스키마 대조를 통과했다"는 뜻이기 때문이다.
기동 후 DB에서 직접 확인하려면:

```sql
SELECT version, description, success FROM flyway_schema_history;   -- 1 | init | 1
SHOW TABLES;                                                       -- users, refresh_tokens, flyway_schema_history
```

### Repository (3단계)

- `UserRepository`(user): `findByProviderAndProviderId(Provider, String)`, `existsByEmail(String)`
  - **user 패키지 밖에서 주입하지 않는다.** auth가 회원 데이터에 접근할 땐 `UserService`를 경유한다
- `RefreshTokenRepository`(auth)
  - `findByTokenHash(String)` — `@EntityGraph(attributePaths = "user")` (재발급 시 User가 항상 필요)
  - `deleteByTokenHash(String)`, `deleteAllByUserId(Long)` — 둘 다 `@Modifying @Query` 벌크 삭제
    - `deleteAllByUserId`는 `@ManyToOne User user`의 속성 경로로 파생된다. `User` 엔티티를 넘기지 않으므로 탈퇴 시 auth가 회원을 로딩할 필요가 없다
    - `@Modifying(clearAutomatically = true)`만 붙이면 된다. 탈퇴 흐름에서 `user.withdraw()`와 이 삭제는 **서로 다른 트랜잭션**이라 flush 순서가 얽히지 않는다 (`flushAutomatically` 불필요)

### user / auth 경계 메서드 (7단계에서 구현)

- `UserService.findOrCreateKakaoUser(providerId, email, nickname)` → `KakaoUserResolution(User, boolean isNewUser)`
  - 조회 → ACTIVE 재사용 / WITHDRAWN `reactivate()` / 없으면 신규 생성(email 중복이면 email만 null)
  - **이 판단 전체를 user가 소유한다.** auth는 카카오에서 받은 값만 넘긴다
  - 재활성화는 기존 회원이므로 `isNewUser=false`
- `UserService.withdraw(userId)` → `String providerId` (소프트 삭제만 수행)
- `AuthService.revokeAllTokens(userId)` / `AuthService.unlinkKakao(providerId)` — 탈퇴 시 `UserController`가 호출

### 카카오 연동 (4단계)

- `domain/auth/config/KakaoProperties`(`prefix="kakao"`): `clientId, clientSecret, adminKey, redirectUri, authorizeUri, tokenUri, userInfoUri, unlinkUri`
  - **`redirectUri`는 BE 콜백 주소**(`{api-host}/api/auth/kakao/callback`)다. FE 주소가 아니다
- `domain/auth/config/RestClientConfig`: kauth/kapi용 `RestClient` Bean 2개 (신규 의존성 불필요 — `RestClient`는 `starter-web`에 포함. `starter-oauth2-client`·webflux 사용 안 함)
- `domain/auth/client/KakaoOAuthClient`: `requestToken(code)`, `fetchUserInfo(kakaoAccessToken)`, `unlink(providerId)`
  - **카카오 호출 실패는 이 경계에서 `BusinessException`으로 변환** — 상위 계층이 카카오의 존재를 모르게 한다
  - 토큰 요청 form: `grant_type=authorization_code`, `client_id`, `client_secret`, `redirect_uri`(설정값), `code`
- `client/dto/KakaoTokenResponse`, `KakaoUserInfoResponse`(+중첩 `KakaoAccount`, `Profile`) — record + `@JsonNaming(SnakeCaseStrategy)`
- 엔드포인트: 인가 `GET https://kauth.kakao.com/oauth/authorize` · 토큰 `POST https://kauth.kakao.com/oauth/token` · 사용자정보 `GET https://kapi.kakao.com/v2/user/me` · 연결해제 `POST https://kapi.kakao.com/v1/user/unlink`(Admin Key)

**체크포인트**
- [ ] **(선행) 카카오 개발자 콘솔 Redirect URI를 BE 콜백 주소로 등록** — local(`http://localhost:8080/api/auth/kakao/callback`)·prod 둘 다. 누락 시 카카오가 `KOE006`으로 거부해 첫 시도부터 실패한다
- [ ] (2단계) Flyway가 두 테이블을 정상 생성하고 `validate` 통과
- [ ] (3단계) `(provider, providerId)` 중복 가입 방지 — 유니크 제약이 엔티티에도 선언돼 H2 테스트에서 재현되는지 함께 확인
- [ ] (4단계) `KakaoOAuthClient`가 API 실패 시 `BusinessException`으로 변환 (`MockRestServiceServer`)

---

## H. Spring Security 설정 (6단계)

`global/config/SecurityConfig`, `global/security/JwtAuthenticationEntryPoint`

- **인증 실패 응답은 `JwtAuthenticationEntryPoint` 하나가 전담한다.** 401뿐 아니라 **403(`WITHDRAWN_USER`)도 여기서 나간다**
- `AccessDeniedHandler`를 두지 않는 근거: J 항목의 필터가 예외 시 `SecurityContext`를 비운 채 체인을 통과시키므로, Spring Security는 이를 **미인증(anonymous)** 으로 보고 항상 `AuthenticationEntryPoint`를 호출한다. `AccessDeniedHandler`는 *인증은 됐는데 권한이 부족한* 경우에만 불리므로 현재 설계에는 도달 경로가 없다 (ADMIN 권한 체크가 들어오는 [[phase6-finalization]]에서 재검토)

| 엔드포인트 | 인증 |
|---|---|
| `GET /api/auth/kakao/authorize` | permitAll (로그인 시작 — 브라우저 네비게이션) |
| `GET /api/auth/kakao/callback` | permitAll (카카오가 리다이렉트하는 착지점) |
| `POST /api/auth/refresh` | permitAll (쿠키 자체가 자격증명) |
| `POST /api/auth/logout` | permitAll (access token 만료 상태에서도 로그아웃 가능해야 함) |
| `/api/users/me` (GET/PUT/DELETE) | authenticated |
| 그 외 | `anyRequest().authenticated()` (공개 조회 API는 [[phase6-finalization]] R 항목에서 최종 점검) |

- `csrf().disable()`, `formLogin`/`httpBasic`/`logout` disable, `sessionManagement(STATELESS)`
- `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
- **CSRF 토큰을 두지 않는 근거**: `SameSite=Strict`가 cross-site 요청에 쿠키를 붙이지 않아 원천 차단됨. 위 "확정 사항"의 도메인 전제가 깨지면 CSRF 토큰 도입이 **필수**가 된다
- **CORS `allowCredentials(true)` 필수** (쿠키 왕복). `allowedOrigins`는 `app.cors.allowed-origins` 명시 목록
  - CORS가 필요한 것은 `/refresh`·`/logout`·`/users/me` **뿐**이다. `/kakao/authorize`·`/kakao/callback`은 XHR이 아니라 브라우저 top-level 네비게이션이라 CORS가 적용되지 않는다
- 401/403 응답도 공통 `ApiResponse` 포맷을 따라야 하는데 필터 단계라 `GlobalExceptionHandler`를 타지 않는다 → 핸들러에서 `ObjectMapper`로 직접 JSON 작성
- ⚠️ 이때 **HTTP 상태를 하드코딩하지 말고 `errorCode.getHttpStatus()` 사용** — EntryPoint가 403(`WITHDRAWN_USER`)까지 함께 내리므로, `401`을 박아두면 A008이 401로 뭉개진다

**체크포인트**
- [ ] 비인증 접근 시 401(A001), CORS 통과/차단 동작 (preflight 포함, `credentials: 'include'`)
- [ ] EntryPoint가 `errorCode.getHttpStatus()`로 401/403을 분기 (A008이 401로 뭉개지지 않는지)

---

## I. JwtProvider + Refresh Token 발급·검증 (5·7단계)

`domain/auth/token/JwtProvider`, `domain/auth/token/SecureTokenGenerator`

- jjwt 0.12.x. 키는 `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` — HS256이므로 `jwt.secret` 최소 32바이트
- `createAccessToken(User)`: subject=userId, claim `role`, `jwt.access-expiration`(30분)
- `parseClaims(token)`: `ExpiredJwtException`/`JwtException`을 그대로 던져 필터가 종류별로 구분하게 함
- **`JwtProvider`는 access token만 담당** — refresh token 생성은 `SecureRandom` 32바이트 → base64url 43자, 별도 컴포넌트가 맡는다
- `jwt.refresh-expiration`(7일)의 용도는 `RefreshToken.expiresAt` 계산 + 쿠키 `Max-Age` (JWT `exp` 아님)
- **재발급**: 쿠키 원문을 SHA-256 해시 → `findByTokenHash` → row 존재 && 미만료면 **신규 access token만 발급**. 미존재·만료 모두 `REFRESH_TOKEN_NOT_FOUND`(A007)
- rotation을 하지 않으므로 **재발급 응답에 `Set-Cookie`가 없다.** 여러 탭이 동시에 호출해도 모두 성공한다
- 만료 row 정리 배치는 후속 과제 (`expiresAt` 검사로 거절되므로 기능상 무해)

**체크포인트**
- [ ] 재발급 시 access token만 갱신되고 refresh 쿠키는 유지 (응답에 `Set-Cookie` 없음)
- [ ] 동일 refresh token으로 연속 재발급 시 매번 성공
- [ ] `token_hash`에 원문이 아닌 SHA-256 해시가 저장됨
- [ ] `expiresAt` 지난 토큰으로 재발급 시 A007 (row가 남아 있어도 거절)
- [ ] 로그아웃 직후 같은 토큰으로 재발급 시 A007

---

## J. JwtAuthenticationFilter + CustomUserDetailsService (6단계)

- `CustomUserDetailsService`(`global/security`): `loadUserByUsername(userId)` → `findById` → `UserPrincipal` 반환
  - **매 요청 DB 조회** — JWT claim만 믿지 않고 탈퇴 등 최신 상태를 즉시 반영하기 위함
  - **거부 사유를 구분해서 던진다**: 미존재 `UsernameNotFoundException` / WITHDRAWN `DisabledException`
- `JwtAuthenticationFilter`(`OncePerRequestFilter`): `Bearer` 토큰 없으면 그냥 통과. 있으면 파싱 → 조회 → `SecurityContextHolder` 설정
  - **예외 시 short-circuit 하지 않는다.** request attribute에만 사유를 남기고 체인을 계속 진행 → 응답은 H 항목의 `JwtAuthenticationEntryPoint`가 attribute를 읽어 결정(401·403 모두)

| 상황 | ErrorCode |
|---|---|
| 토큰 없음 / 기타 | `UNAUTHORIZED`(A001, 401) |
| 서명 위조·형식 오류 | `INVALID_TOKEN`(A003, 401) |
| 만료 | `EXPIRED_TOKEN`(A004, 401) |
| 탈퇴 회원 | **`WITHDRAWN_USER`(A008, 403)** |

⚠️ 탈퇴 사유를 attribute로 넘기지 않으면 A008은 **절대 반환되지 않는 죽은 코드**가 된다(전부 A001으로 뭉개짐).

**체크포인트**
- [ ] 만료/위조 토큰이 A004/A003로 **구분**되어 응답
- [ ] 탈퇴 회원의 유효한 access token → **A008(403)** (A001으로 뭉개지면 실패)

---

## K. AuthController / UserController (7~9단계)

| Method | URI | 인증 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/auth/kakao/authorize` | 불필요 | - | **302** → 카카오 + `Set-Cookie: oauth_state` |
| GET | `/api/auth/kakao/callback` | 불필요 | `?code=&state=` (카카오가 채움) | **302** → FE + `Set-Cookie: refresh_token` |
| POST | `/api/auth/refresh` | 불필요 | 없음 (쿠키) | `{accessToken, tokenType, accessTokenExpiresIn}` — `Set-Cookie` 없음 |
| POST | `/api/auth/logout` | 불필요 | 없음 (쿠키) | `null` + 쿠키 만료 `Set-Cookie` |
| GET | `/api/users/me` | 필요 | - | `{id, email, name, phone, address, birthDate, role, provider, createdAt}` |
| PUT | `/api/users/me` | 필요 | `{name?, phone?, address?, birthDate?}` 모두 optional | 위 GET과 동일 |
| DELETE | `/api/users/me` | 필요 | - | `null` |

- **`/kakao/authorize`·`/kakao/callback`은 JSON을 반환하지 않는다.** 항상 302다 — 브라우저 네비게이션이라 `ApiResponse` 포맷이 사용자 화면에 노출되면 안 된다. 실패도 예외를 던지지 않고 `{FE}/oauth/complete?error={code}`로 302해 **FE가 에러 UI를 소유**하게 한다
- **응답 `data`에 `refreshToken`을 절대 포함하지 않는다** — 오직 `Set-Cookie`로만
- 로그인 사용자 식별은 `@AuthenticationPrincipal`(`UserPrincipal`)로 일관되게

### 쿠키 2종

둘 다 `HttpOnly` + `Secure` + `Path=/api/auth` + `Domain` 미설정(host-only). **`SameSite`만 다르다.**

| | `refresh_token` | `oauth_state` |
|---|---|---|
| 값 | 불투명 랜덤 원문(base64url 43자) | 랜덤 state(base64url) |
| `SameSite` | `Strict` | **`Lax`** |
| `Max-Age` | `jwt.refresh-expiration`(7일) | **5분** (인가 왕복에만 필요) |
| 수명 | 로그아웃·탈퇴까지 | 콜백에서 **1회 사용 후 즉시 삭제** |

⚠️ **`SameSite`를 둘 다 `Strict`로 맞추면 로그인이 전부 실패한다.** 콜백은 `kauth.kakao.com` → API 호스트로의 **cross-site top-level GET 네비게이션**인데, `Strict` 쿠키는 여기서 전송되지 않는다. `Lax`는 top-level GET 네비게이션에 한해 전송되므로 `oauth_state`는 반드시 `Lax`여야 한다.

반대로 `refresh_token`은 FE(`app.`) → API(`api.`)의 **same-site XHR**에만 쓰이므로 `Strict`가 맞다. 콜백 응답에서 이 쿠키를 **심는 것**은 SameSite와 무관하게 항상 동작한다 — SameSite는 브라우저가 쿠키를 *보낼 때*만 관여한다.

- `Path=/api/auth`로 좁혀 그 외 API 요청엔 실리지 않게 한다
- `Secure`는 로컬 개발에도 문제없다 (브라우저가 `http://localhost`를 secure context로 취급)
- ⚠️ **쿠키 삭제 시에도 `Path=/api/auth`를 반드시 함께 지정**한다. `Max-Age=0`만 내리면 Path가 `/`로 해석돼 매칭되지 않아 **쿠키가 지워지지 않는다**. `oauth_state` 삭제에도 동일 적용

### 로그인 전체 흐름

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
카카오로 보낼 쿼리: `client_id`, `redirect_uri`(설정값), `response_type=code`, `state`

**`/callback` 처리 순서**
1. 카카오가 `?error=`를 실어 보낸 경우(사용자가 동의 거부 등) → 그대로 FE로 302 `?error=`
2. `oauth_state` 쿠키와 쿼리 `state` 대조 → 불일치·누락이면 `INVALID_OAUTH_STATE`(A009)
3. **`oauth_state` 쿠키 즉시 삭제** (1회용)
4. `requestToken(code)` → `fetchUserInfo` → kakaoId, email(nullable), nickname
5. `userService.findOrCreateKakaoUser(kakaoId, email, nickname)` → `KakaoUserResolution(User, isNewUser)`
   - ACTIVE → 그대로 사용 / WITHDRAWN → `reactivate()` (자동 재활성화). 둘 다 `isNewUser=false`
   - 없음 → 신규 생성, `isNewUser=true`. **email이 중복이면 email만 null 처리하고 가입은 계속 진행**
   - ⚠️ 이 판단은 **user 도메인 안에서** 한다. `AuthService`가 `UserRepository`를 직접 호출하지 않는다
6. refresh 원문 생성 → 해시를 `RefreshToken` row로 저장 → 원문을 `refresh_token` 쿠키로 설정
7. `{app.frontend.base-url}/oauth/complete`로 302. 신규 가입이면 `?isNewUser=true`
   (`isNewUser`는 자격증명이 아니라 "추가 정보 입력" UX 용도이므로 query param으로 안전. 게이팅 아님)

**access token은 이 흐름에서 발급하지 않는다** — 7단계의 `/api/auth/refresh`가 발급한다.
그 결과 **access token이 URL에 실리는 경로가 설계상 존재하지 않고**, 로그인 직후와 새로고침 후의 세션 획득 경로가 동일해진다.

### 회원 탈퇴 (`DELETE /api/users/me`)

user와 auth 양쪽을 건드리는 **유일한 흐름**이다. 순서를 `UserController`가 소유해
`UserService`가 `AuthService`를 참조하지 않게 하고, 그 결과 의존 방향이 `auth → user` 단방향으로 유지된다.

```
1. authService.revokeAllTokens(userId)          # RefreshToken 전수 삭제
2. String providerId = userService.withdraw(userId)   # 소프트 삭제 + providerId 획득
3. authService.unlinkKakao(providerId)          # 카카오 연결 해제 (best-effort)
4. refresh_token 쿠키 만료 (Path=/api/auth 필수)
```

- 1은 **fail-closed** — 실패하면 예외가 전파되어 탈퇴가 중단되고 회원은 ACTIVE로 남는다(재로그인으로 복구 가능)
- 3은 **best-effort** — 실패해도 로그만 남기고 탈퇴는 성립한다(카카오 장애로 탈퇴가 막히면 안 됨).
  예외를 삼키는 판단은 호출자가 아니라 `AuthService` 안에서 한다
- ⚠️ **순서를 뒤집지 말 것.** 2를 먼저 하면 WITHDRAWN인데 `refresh_tokens` row가 남는 상태가 생긴다
- 1과 2는 별도 트랜잭션이라 원자적이지 않다. 어느 쪽이 실패해도 결과가 fail-safe하고, 카카오 `unlink`는 외부 호출이라
  애초에 트랜잭션으로 묶을 수 없다. 원자성이 필요해지면 도메인 이벤트로 전환한다(**현 단계에서는 도입하지 않음**)

### ErrorCode (`AuthErrorCode`, `domain/auth/exception`)

인증 관련 코드는 전부 `AuthErrorCode`(A 접두사)에 있다. `UserErrorCode`에는 `DUPLICATE_EMAIL`(U001)만 남는다.

```
UNAUTHORIZED(401, A001, "인증이 필요합니다.")
FORBIDDEN(403, A002, "접근 권한이 없습니다.")
INVALID_TOKEN(401, A003, "유효하지 않은 토큰입니다.")
EXPIRED_TOKEN(401, A004, "만료된 토큰입니다.")
KAKAO_AUTH_FAILED(400, A005, "카카오 인증에 실패했습니다.")
KAKAO_API_ERROR(502, A006, "카카오 서버 연동 중 오류가 발생했습니다.")
REFRESH_TOKEN_NOT_FOUND(401, A007, "유효하지 않거나 만료된 리프레시 토큰입니다.")
WITHDRAWN_USER(403, A008, "탈퇴한 회원입니다.")
INVALID_OAUTH_STATE(400, A009, "잘못된 인증 요청입니다.")
```
- `INVALID_TOKEN`(A003)/`EXPIRED_TOKEN`(A004)은 JWT 자체의 서명/만료 오류에 쓴다
- `WITHDRAWN_USER`(A008)가 auth에 있는 이유: 회원 상태에서 파생되지만 실제 발생 지점은
  `CustomUserDetailsService`/`JwtAuthenticationFilter`이고 "인증 거절 사유"로 소비된다
- A005·A006·A009는 콜백에서 발생하는데 **콜백은 JSON을 반환하지 않으므로**, 이 코드들은 FE 복귀 URL의 `?error=` 값으로 전달된다 (`{FE}/oauth/complete?error=A009`)
- `INVALID_CREDENTIALS`(옛 U002)는 카카오 전용 설계에서 도달 경로가 없어 **삭제**했다. LOCAL 로그인 도입 시 `AuthErrorCode`에 다시 추가한다

### 설정 값

- `application.yml`(고정 URL): `kakao.authorize-uri`, `token-uri`, `user-info-uri`, `unlink-uri`
- `application-local.yml`/`application-prod.yml`(env 기반, gitignore): `kakao.client-id`, `client-secret`, `admin-key`, `redirect-uri`, `app.frontend.base-url`, `app.cors.allowed-origins`
  - `kakao.redirect-uri` — **BE 콜백** (local: `http://localhost:8080/api/auth/kakao/callback`)
  - `app.frontend.base-url` — 로그인 완료 후 복귀 대상 (local: `http://localhost:3000`)
- 로컬에서도 성립한다: `localhost:3000`(FE) ↔ `localhost:8080`(BE)은 포트가 달라도 **same-site**라 `SameSite=Strict`인 `refresh_token`이 정상 전송된다

### FE 계약 (저장소 범위 밖, API 계약의 반대편)

FE는 **인가코드를 다루지 않는다.** authorize URL 조립도, 콜백 `code` 추출도 FE 몫이 아니다.

1. 로그인 버튼 → `location.href = '{API_BASE}/api/auth/kakao/authorize'` — XHR이 아니라 **페이지 이동**이어야 한다 (fetch로 호출하면 302를 따라가 버려 카카오 로그인 화면이 뜨지 않는다)
2. `/oauth/complete` 페이지를 만들고, 거기서 `POST /api/auth/refresh`를 호출해 accessToken을 얻는다. `?error=`가 있으면 에러 UI, `?isNewUser=true`면 추가 정보 입력 UX
3. 인증 요청에 `credentials: 'include'` (없으면 쿠키가 오가지 않는다)
4. access token은 **JS 변수(메모리)에만** 보관
5. 새로고침 시 access token이 소멸하므로 **앱 부팅 시 `/api/auth/refresh` 호출로 세션 복구**(silent refresh). 401이면 미로그인 처리 — 2번과 동일한 호출이라 로직을 공유할 수 있다
6. 401 인터셉터: refresh → 성공 시 원요청 재시도, 실패 시 로그인 화면
7. refresh 단일 Promise 묶기는 권장(필수 아님) — rotation이 없어 동시 호출도 모두 성공한다

**체크포인트**
- [ ] `/kakao/authorize` 접속 → 카카오 로그인 → `/oauth/complete` 복귀 → silent refresh → `GET /api/users/me`까지 **브라우저에서 전체 플로우 통과**
- [ ] **`oauth_state` 쿠키가 콜백 요청에 실제로 실려 오는지** (`Lax`가 아니면 여기서 깨진다 — 가장 먼저 확인할 것)
- [ ] state 불일치·누락 시 A009로 FE에 `?error=` 전달, 콜백 재사용(뒤로가기) 시에도 A009
- [ ] 카카오 동의 화면에서 취소 시 FE로 `?error=` 전달 (예외 페이지 노출 없음)
- [ ] 콜백 응답이 **JSON이 아니라 302**인지, `ApiResponse` 포맷이 브라우저에 노출되지 않는지
- [ ] **어떤 응답에도 access token이 URL/쿼리스트링에 실리지 않는지**
- [ ] 동일 계정 재로그인 시 기존 User로 인증 (신규 생성 아님) / 탈퇴 후 재로그인 시 자동 재활성화(ACTIVE)
- [ ] `refresh_token` 쿠키에 `HttpOnly`/`Secure`/`SameSite=Strict`/`Path=/api/auth` 전부 포함
- [ ] 쿠키 없이 `/api/auth/refresh` → A007(401)
- [ ] 로그아웃·탈퇴 후 브라우저에서 쿠키가 **실제로 사라지는지** (`Path` 누락 시 안 지워짐)

**도메인 경계 체크포인트**
- [ ] `domain/user` 어디에도 `domain.auth` import가 없는지 (역방향 의존 없음)
- [ ] `domain/auth` 어디에도 `UserRepository` 주입이 없는지 (`UserService`만 경유)
- [ ] 신규 카카오 로그인 시 User가 `AuthService`가 아니라 `UserService.findOrCreateKakaoUser`에서 생성되는지
- [ ] 탈퇴 중 `revokeAllTokens`가 실패하면 회원이 ACTIVE로 남는지 (fail-closed)
- [ ] 카카오 `unlink` 실패가 탈퇴를 막지 않는지 (로그만 남고 200)

---

## 향후 개선 항목

- **PKCE(S256)** — authorization code injection 대응. 공격자가 자기 브라우저로 흐름을 시작한 뒤 피해자의 인가코드를 콜백에 끼워넣는 공격인데, ⚠️ **`state`로는 막히지 않는다**(공격자 자신의 state는 유효하므로 검증을 통과한다). `client_secret`도 `redirect_uri` 고정도 막지 못한다 — 공격자 대신 우리 서버가 정상 값을 써주기 때문. 이를 막는 것은 PKCE뿐이다.
  - *비용이 크게 낮아졌다*: BE가 redirect를 소유하게 되면서 **FE 부담(Web Crypto·sessionStorage)이 사라졌고 BE 단독 구현이 되었다.** verifier를 `oauth_state` 쿠키에 함께 보관하면 새 부품도 필요 없다
  - *남은 보류 사유는 하나*: 카카오 discovery는 `code_challenge_methods_supported: ["S256"]`을 명시하나 한글 REST API 문서에는 없다 → **도입 전 authorize→token 교환 수동 검증 필수**
  - *완화 요인*: 전환 후 인가코드가 FE를 거치지 않아(카카오 → BE 직행) 유출 표면 자체가 크게 줄었다
- **Rotation + 재사용 탐지** — 둘은 함께 도입해야 의미가 있다. 도입 시 멀티탭 경합 대응이 반드시 따라온다: 기존 row를 즉시 삭제하지 말고 `expiresAt`을 `now()+30초`로 단축하는 서버측 grace period + FE 단일 Promise 묶기. 로그아웃·탈퇴는 grace 없이 즉시 삭제
- **LOCAL 로그인** — `V2__add_password.sql` + `PasswordEncoder`(BCrypt) Bean + `Provider.LOCAL` + 이메일 중복 검사(U001)
- **만료 `refresh_tokens` row 정리 배치**
- **ADMIN 계정 발급 방식** — [[phase6-finalization]] R 항목에서 결정

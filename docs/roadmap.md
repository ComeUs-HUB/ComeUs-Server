# 진행 현황

**이 파일이 진행 상태의 유일한 출처다.** 다른 문서는 상태를 적지 않는다 —
계약은 [architecture.md](architecture.md)·[domains/](domains/)·[conventions/](conventions/)가,
결정 근거는 [decisions/](decisions/)가 소유한다.

> 기준: 2026-10-08 · 브랜치 `feature/phase4-cart`

## 요약

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 환경 설정 — 프로젝트 골격, 의존성, 설정 파일 | ✅ |
| 1 | 글로벌 기반 — 공통 응답, 예외 처리, 페이지네이션 | ✅ |
| 2 | 인증/인가 — user/auth 도메인, Security, JWT | ✅ (브라우저 E2E 통과) |
| 3 | 카테고리 / 상품 | ✅ 카테고리 · 상품 모두 MySQL 실기동 통과 |
| 4 | 구매 플로우 — Cart · Order · Payment | ⬜ |
| 5 | 리뷰 | ⬜ |
| 6 | 마무리 — RBAC 최종 점검 | ⬜ |
| 7 | 배포 — Docker, CI/CD, 서버, 모니터링 | ⬜ ([deployment.md](deployment.md)) |

**Phase 4 Cart(1~3단계)가 끝났다.** MySQL 동시성 확인은 남아 있다(아래 Cart 체크리스트). 다음은 Order다.
**동시성 제어 방식은 Order 착수 전까지 ADR로 정한다** — 되돌리기 비용이 큰 결정이지만
재고를 차감하는 것은 Order이고 Cart는 읽기만 하므로 Cart는 그 결정 없이 착수할 수 있다
([domains/purchase/order.md](domains/purchase/order.md)).

**현재 규모**: main 79개 파일 · 테스트 27개 클래스 353개 케이스.
케이스 수는 `./gradlew test` 리포트 기준이라 **파라미터라이즈는 실행 횟수로 센다**.

## Phase 0 — 환경 설정 ✅

Spring Boot 3.5.0 + Java 21 + Gradle(Kotlin DSL) 골격, 의존성, 프로파일별 설정이 모두 갖춰졌다.

- 활성 프로파일은 **`local`**이다(설계 초안의 `dev`가 아님)
- `application-local.yml`·`application-prod.yml`은 `.gitignore` 대상 — 의도대로 동작 확인
- `src/test/resources/application.yml`에 H2 인메모리 테스트 설정 준비됨
- `JpaConfig`의 `@EnableJpaAuditing`, `BackendApplication`의 `@ConfigurationPropertiesScan` 적용
- Flyway(`flyway-core` + `flyway-mysql`)는 Phase 2에서 함께 도입됨
  ([ADR 0008](decisions/platform/0008-flyway-owns-schema.md))

도메인 패키지는 **미리 만들지 않는다.** `cart`·`order`·`payment`·`review`에는
현재 `exception/*ErrorCode`만 있고, 나머지 하위 폴더는 각 Phase 착수 시점에 만든다
(`category`와 `product`는 Phase 3에서 채워졌다).
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

검증: `ApiResponseTest` 12케이스 — `ApiResponse` 생성자의 유효·무효 조합.

남은 것:
- [x] `PageResponse`를 실제로 쓰는 목록 조회 API — Phase 3 Product에서 적용됐다
      (`ProductService.getList`, `GET /api/products`, 비회원 공개)
- [ ] `BusinessException`을 상속하는 도메인별 구체 예외 클래스 — 필요해지는 시점에

## Phase 2 — 인증/인가 ✅

카카오 OAuth 로그인 왕복(302·쿠키)부터 JWT 인증 필터, 회원 API, 탈퇴 오케스트레이션까지 동작한다.
계약은 [domains/auth.md](domains/auth.md), 결정 근거는 [decisions/](decisions/)에 있다.

구현 순서는 9단계로 나눴고, 각 클래스 Javadoc 첫 줄에 `[Phase 2 · N단계]`로 소속 단계를 표기한다.
초기에 쓰던 task 알파벳(`[N단계 · G]`)은 추적되지 않는 식별자라 폐기했다
([conventions/javadoc.md](conventions/javadoc.md)).

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

### 자동 테스트 — 13개 클래스 142개 케이스

| 클래스 | 케이스 |
|---|---|
| `AuthServiceTest` | 25 |
| `AuthControllerTest` | 21 |
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

> `AuthControllerTest`는 이전에 16으로 적혀 있었다. `@Test` 메서드만 센 값이고, `@ParameterizedTest` 하나가 5회 실행된다 — Gradle 리포트와 맞추어 21로 고쳤다.

## Phase 3 — 카테고리 / 상품 ✅

계약: [domains/catalog/](domains/catalog/README.md).
**카테고리·상품 모두 끝났고 각각 MySQL 실기동까지 통과했다.** 근거가 된 결정은 넷이다 —
[ADR 0011](decisions/catalog/0011-jpa-specification-for-product-filter.md)·[0012](decisions/catalog/0012-image-urls-in-separate-table.md)·[0013](decisions/catalog/0013-hide-soft-deleted-products-everywhere.md)·[0014](decisions/catalog/0014-category-reads-product-repository.md).

### Category — 1~4단계 ✅

구현 순서는 4단계로 나눴고, Phase 2와 같이 각 클래스 Javadoc 첫 줄에 `[Phase 3 · N단계]`가 표기돼 있다.

| 단계 | 내용 | 검증 |
|---|---|---|
| 1 | ADMIN 승격 경로(`User.promoteToAdmin`) + 인가 실패 403(`JwtAccessDeniedHandler`) | 4단계에서 함께 |
| 2 | 엔티티 + 스키마 (`V2__create_categories.sql`) | `CategoryTest`, `CategoryRepositoryTest` |
| 3 | 서비스 — 트리 조립·생성·수정·삭제 규칙 | `CategoryServiceTest` |
| 4 | 컨트롤러 + `SecurityConfig` 배선(permitAll·`hasRole`) | `CategoryControllerTest` + MySQL 실기동 |

이 Phase에서 **처음 생긴 것이 셋**이다. 비회원에게 열린 조회 API, ADMIN 인가 판정
([ADR 0010](decisions/platform/0010-access-denied-handler-for-authorization.md)), 그리고 경로 변수
(`/api/categories/{id}` — `MethodArgumentTypeMismatchException` 핸들러가 이때 필요해졌다).

### MySQL 실기동 (2026-09-15)

테스트는 H2 `create-drop`이라 `V2__create_categories.sql`을 **실행하지 않는다.** 스키마가 엔티티와
어긋나도 `./gradlew test`는 통과하므로, 아래는 실기동으로만 확인되는 것들이다.

- [x] `V2` 최초 적용 — `v1 → v2`, `flyway_schema_history`에 `success=1`
- [x] Hibernate `validate` 통과 — 엔티티 매핑과 `categories` 스키마 일치
- [x] InnoDB가 `parent_id` 인덱스를 자동 생성(`KEY fk_categories_parent`) —
      V2가 별도 `INDEX`를 두지 않은 전제가 맞았다
- [x] 비회원 `GET /api/categories` 200 + 빈 배열
- [x] **USER 403(A002) → DB에서 `role`만 ADMIN으로 변경 → 같은 토큰으로 201.**
      권한이 토큰 claim이 아니라 매 요청 DB에서 읽힘이 실제로 확인됐다
- [x] 201 응답의 `Location`이 절대 URL이 아닌 상대 경로(`/api/categories/1`)
- [x] 3depth CA003(400) · 하위 있는 삭제 CA002(409) · 없는 id CA001(404) · `/abc` C001(**400**, 500 아님)
- [x] `displayOrder` 정렬이 **실제 SQL**에서 동작 — 서비스 테스트는 mock이 정렬된 결과를 돌려주므로
      `findAllByOrderByDisplayOrderAscIdAsc`는 여기서 처음 검증됐다
- [ ] **브라우저 왕복은 미확인.** FE 페이지가 없어 카카오 콜백 리다이렉트를 완주할 수 없다 —
      access token을 `.env`의 `JWT_SECRET`으로 직접 서명해 대체했다.
      카카오 왕복 자체는 Phase 2 E2E에서 검증됐다

### 자동 테스트 — 4개 클래스 59개 케이스

| 클래스 | 케이스 |
|---|---|
| `CategoryControllerTest` | 22 |
| `CategoryServiceTest` | 21 |
| `CategoryRepositoryTest` | 10 |
| `CategoryTest` | 6 |

> `CategoryServiceTest`(15→21)와 `CategoryRepositoryTest`(5→10)는 상품 작업에서 늘었다 —
> 7단계의 `collectSelfAndChildIds`·`findSelfAndChildIds`와 8단계의 CA004 검사 때문이다.

### Product — 5~8단계 ✅

카테고리가 1~4단계를 썼으므로 상품은 5단계부터 잇고,
Javadoc 표기도 `[Phase 3 · 5단계]`처럼 이어 붙인다.
브랜치는 `feature/phase3-product`(master `2db1205`에서 분기).

| 단계 | 내용 | 검증 | |
|---|---|---|---|
| 5 | 엔티티(`Product`·`ProductImage`·`ProductStatus`) + `V3__create_products.sql` | `ProductTest` | ✅ |
| 6 | Repository + `ProductSpecifications` — 조립을 한곳에 모은다 | `ProductRepositoryTest` | ✅ |
| 7 | 조회 서비스 + DTO 4종, `CategoryService`에 자식 id 조회 추가 | `ProductServiceTest` | ✅ |
| 8 | 컨트롤러 + `SecurityConfig` 배선 + 카테고리 삭제 차단(CA004) | `ProductControllerTest` + MySQL 실기동 | ✅ |

#### 8단계 — 완료

- [x] 컨트롤러 조회 둘(`getList`·`getDetail`)과 `sanitize` —
      정렬 화이트리스트 위반은 C001(400), `size`는 100에서 잘린다
- [x] **쓰기 셋** — `ProductService`의 등록·수정·삭제(soft delete)와 POST/PUT/DELETE.
      `HIDDEN`까지 읽는 조회를 쓰므로 목록·상세와 헬퍼를 공유하지 않는다([ADR 0013](decisions/catalog/0013-hide-soft-deleted-products-everywhere.md))
- [x] `SecurityConfig` 조회 permitAll — `GET /api/products`와 `/api/products/*` 둘 다 연다
- [x] `SecurityConfig` 쓰기 `hasRole('ADMIN')` — POST는 컬렉션, PUT·DELETE는 단건 경로다
- [x] 카테고리 삭제 차단 CA004 — `CategoryService`가 `ProductRepository`를 직접 읽는다.
      ADR 0014가 예고한 역방향 참조가 여기서 처음 생겼다
- [x] `ProductControllerTest` — 조회 22 + 쓰기 6
- [x] MySQL 실기동 (아래 체크리스트)

> CA004를 넣으며 `existsByCategoryIdIn(List)`을 `existsByCategoryId(Long)`으로 좁혔다.
> 하위가 있으면 CA002가 먼저 막아 잎만 도달하므로 목록을 받을 이유가 없다 —
> [ADR 0014](decisions/catalog/0014-category-reads-product-repository.md) 본문에는 옛 이름으로 남아 있다.

#### 자동 테스트 — 4개 클래스 103개 케이스

| 클래스 | 케이스 |
|---|---|
| `ProductControllerTest` | 28 |
| `ProductRepositoryTest` | 28 |
| `ProductServiceTest` | 28 |
| `ProductTest` | 19 |

> `ProductRepositoryTest`가 29→28인 것은 `existsByCategoryIdIn`의 목록 동작을 보던 케이스가
> 시그니처 축소로 사라졌기 때문이다.

이 Phase에서 **처음 생기는 것이 넷**이다 — 페이징·정렬·동적 필터(`PageResponse`의 첫 실사용,
Specification 도입), soft delete, 도메인 간 **양방향** 참조
([ADR 0014](decisions/catalog/0014-category-reads-product-repository.md)),
그리고 `Category`가 의도적으로 피했던 `@OneToMany` 컬렉션(이미지).

#### MySQL 실기동 (2026-09-26)

테스트는 H2 `create-drop`이라 `V3__create_products.sql`을 **실행하지 않는다.**
스키마가 엔티티와 어긋나도 `./gradlew test`는 통과하므로, 아래는 `local` 프로파일 실기동으로만
확인된다. **착수 전에 고정해 둔 열 항목을 그대로 확인했다.**

- [x] `V3` 최초 적용 — `v2 → v3`, `flyway_schema_history`에 `success=1`
- [x] Hibernate `validate` 통과 — `DECIMAL(10,2)`와 `VARCHAR` enum 매핑이 여기서 처음 대조됐다
- [x] 비회원 `GET /api/products` 200 + 빈 `PageResponse`(`totalElements: 0`, `hasNext: false`)
- [x] ADMIN 토큰으로 등록 → 201 + `Location: /api/products/1`(절대 URL 아님).
      응답 바디에 `stockQuantity` 키가 없는 것도 함께 확인됐다([ADR 0015](decisions/catalog/0015-no-stock-quantity-in-public-response.md))
- [x] **대분류 `categoryId` 필터에 소분류 상품이 딸려 나온다** —
      서비스 테스트는 mock이라 여기서 처음 검증됐다
- [x] **soft delete 왕복** — DELETE 200 → 목록 0건 → 상세 404(P001) →
      DB에 `status='HIDDEN'` 행 잔존 → PUT으로 `ON_SALE` 복귀 → 목록에 다시 나타남
- [x] 상품이 붙은 카테고리 DELETE → CA004(409), 상품을 옮긴 뒤 삭제 → 200
- [x] `?sort=foo` → C001(**400**, 500 아님) · `?size=9999` → `pageSize: 100`
- [x] **`@BatchSize`가 실제로 동작** — 상품 5건 목록 조회에 쿼리 **2건**
      (`select products` 1 + `select product_images` 1). 없었다면 1+5=6건이었다.
      `where product_id in (?)`의 플레이스홀더가 100개로, `@BatchSize(size = 100)`이
      페이지 상한과 맞춰져 있음이 드러난다
- [x] USER 토큰으로 쓰기 요청 → 403(A002), 같은 토큰으로 조회는 200
- [ ] **브라우저 왕복은 미확인.** 카테고리 때와 같은 이유다 — FE 페이지가 없어
      access token을 `.env`의 `JWT_SECRET`으로 직접 서명해 대체했다.
      카카오 왕복 자체는 Phase 2 E2E에서 검증됐다

남은 것:
- [ ] **이미지 파일 업로드 API** — 이번 범위 밖이다. `product_images` 테이블과 URL 등록까지만 만들고,
      멀티파트 업로드·로컬 스토리지·URL 발급은 별도로 다룬다
      ([ADR 0012](decisions/catalog/0012-image-urls-in-separate-table.md))

## Phase 4 — 구매 플로우 ⬜

계약: [domains/purchase/](domains/purchase/README.md).
결정 근거는 [ADR 0016](decisions/purchase/0016-stock-reserved-at-order-not-cart.md)·[0017](decisions/purchase/0017-order-copies-cart-and-cuts-the-link.md)·[0018](decisions/purchase/0018-no-product-options-yet.md)이 소유한다.

구현 순서는 **Cart → Order → Payment**다. Cart만 구매 플로우 안에서 들어오는 의존이 없고
재고를 점유하지 않아 되돌리기 비용이 가장 낮다. Payment는 Order 없이 엔티티가 성립하지 않는다.

**동시성 제어 방식은 Cart 다음으로 미룬다.** 재고 차감은 Order의 일이고 Cart는 재고를 읽기만
하므로([ADR 0016](decisions/purchase/0016-stock-reserved-at-order-not-cart.md)) Cart는 그 결정 없이
착수할 수 있다. Order 착수 전까지 정하고 ADR로 남긴다.

### Cart — 1~3단계 ✅

브랜치 `feature/phase4-cart`(master `1af5eaf`에서 분기).
Javadoc 첫 줄에 `[Phase 4 · N단계]`를 표기한다 — Phase 2·3과 같다.

| 단계 | 내용 | 검증 | |
|---|---|---|---|
| 1 | 엔티티(`Cart`·`CartItem`) + `V4__create_carts.sql` | `CartTest` · `CartPersistenceTest` | ✅ |
| 2 | Repository + `CartService` | `CartServiceTest` · `CartRepositoryTest` | ✅ |
| 3 | 컨트롤러 + DTO + `SecurityConfig` 점검 | `CartControllerTest` + MySQL 실기동 | ✅ |

착수하며 확인할 것:

- [x] **`UserService`에 `User` 엔티티를 반환하는 메서드를 더한다** — `Cart`가 `User`를 참조하는데
      공개 메서드 넷이 전부 DTO를 반환한다. `CategoryService.getCategory()`가 같은 자리다.
      `cart → user`·`order → user`가 함께 쓴다([architecture.md](architecture.md))
- [x] **`CartItemRepository`는 두지 않는다** — 모든 쓰기가 `Cart`를 거치고 조회는
      `CartRepository.findByUserId` 하나로 끝난다(ADR 0019가 2단계 판정으로 남긴 항목)
- [x] **주문용 메서드(선택 항목 조회·삭제)는 Order 단계에서 더한다** — 반환 형태·트랜잭션 참여·
      장바구니 락 위치가 Order 설계에 달려 있다([cart.md](domains/purchase/cart.md) "주문에 제공하는 것")
- [x] **`SecurityConfig` 배선이 필요한지 판정** — 불필요하다. `anyRequest().authenticated()`가
      `/api/cart/**`를 막고, 공개 경로는 `/**` 없이 정확한 경로만 연다. Javadoc의 RBAC 표에 행만 더했다
- [x] **요청 수량 상한 N을 정한다** — **99**로 둔다(`CartItemAddRequest.MAX_QUANTITY`). 3단계 DTO에 `@Max(N)`을 걸고 위반은 `C001`이다.
      재고 초과는 그 뒤 `CT002`가 본다. 엔티티는 이미 합산을 `long`으로 계산해
      `int` 오버플로로 재고 검사를 우회하는 경로를 막아 뒀다
- [x] **`V4` 번호 충돌 메모 정리** — 아래 미결 사항의 LOCAL 로그인 항목에서 번호를 고정하지 않고
      "착수 시점의 다음 번호"로 바꿨다. 번호를 박아 두면 다음 도메인이 가져갈 때마다 같은 충돌이 난다
- [ ] **장바구니 락을 MySQL에서 확인한다** — H2는 기본 격리 수준이 READ COMMITTED이고 gap lock도 재현하지 않아
      `./gradlew test`로 검증되지 않는다([ADR 0020](decisions/purchase/0020-cart-writes-use-read-committed.md)).
      같은 회원의 동시 담기 2건이 **합산 4**로 끝나는지(Lost Update 없음), 동시 첫 담기가 교착(500)이 아니라
      `C005`(409)로 끝나는지 본다. **2026-10-08 실기동에서는 하지 않았다** — 아래 기록 참고

### Cart MySQL 실기동 (2026-10-08)

테스트는 H2 `create-drop`이라 `V4__create_carts.sql`을 실행하지 않고, 격리 수준·락도 H2와 MySQL이 다르다.
아래는 실기동으로만 확인되는 것들이다. 테스트 데이터는 확인 후 삭제했다.

- [x] `V4` 최초 적용 — `v3 → v4`, `flyway_schema_history`에 `success=1`
- [x] Hibernate `validate` 통과 — `Cart`·`CartItem` 매핑과 V4 스키마 일치
- [x] uk 두 개(`uk_carts_user_id`, `uk_cart_items_cart_id_product_id`)가 V4대로 생성됐다.
      `cart_id`는 uk의 앞 컬럼이 FK 인덱스를 겸하고, `product_id`는 InnoDB가 `KEY fk_cart_items_product`를 자동 생성했다
- [x] 락 쿼리(`where user_id = ?`)가 유니크 인덱스를 타 **레코드 락**이 된다 — ADR 0020이 전제한 그대로
- [x] API 기본 동작 24개 — 담기(새 장바구니·합산·새 항목), 수량 교체, 항목 삭제·비우기,
      `C001`(0·100·**정수 범위 초과**)·`CT002`·`P001`·`CT001`(타인 항목, 장바구니 없는 회원 포함)·401
- [x] `orphanRemoval`이 MySQL에서 실제 `DELETE`로 반영된다 — 항목 삭제·비우기 후 `cart_items` 행 수 확인
- [x] `purchasable`이 담은 뒤의 재고 부족(5 → 1)과 품절(0)에서 `false`가 되고 항목은 남는다
- [x] **쓰기 요청이 실제로 READ COMMITTED로 실행된다** — general log에 `SET SESSION TRANSACTION ISOLATION LEVEL
      READ COMMITTED` → 트랜잭션 → `REPEATABLE READ` 복원이 찍힌다. 비용은 쓰기 요청당 3문장이다
- [x] 요청당 쿼리 수(인증 필터의 회원 조회 1회 포함) — 조회 2 · 담기 6 · 수량 변경 5 · 항목 삭제 5 · 비우기 4 + N(N ≤ 50)
- [ ] **동시성 확인은 하지 않았다** — 위 "장바구니 락을 MySQL에서 확인한다" 항목이 그대로 남아 있다
- [ ] `existsByUserId`가 `carts left join users`로 실행된다. `carts.user_id`만 보면 되므로 `@Query`로 조인을 없앨 수 있다

### Order — 4~8단계 ⬜

착수 전 **동시성 제어 ADR**이 필요하다. 이 ADR은 함께 정한다 —
**주문 트랜잭션에서 장바구니 루트 락을 어디서 잡고 상품 락과 어떤 순서로 잡는지**, 그리고
**주문 생성·결제의 멱등성 키** 도입 여부(장바구니에는 비용 대비 이득이 작아 두지 않았다).
**주문 트랜잭션도 READ COMMITTED여야 한다** — `CartService` 쓰기를 기본 격리 수준(REPEATABLE READ) 트랜잭션 안에서
부르면 Spring이 격리 수준을 조용히 무시해 Lost Update가 되살아난다([ADR 0020](decisions/purchase/0020-cart-writes-use-read-committed.md)).
`REQUIRES_NEW`는 쓸 수 없다(재고 차감과 항목 삭제가 한 트랜잭션이어야 한다). 무시되지 않게 막는 장치
(`validateExistingTransaction` 또는 진입 시 격리 수준 가드)도 이 ADR에서 정한다. 그 밖에 정할 것은
[order.md](domains/purchase/order.md)의 "착수 전 정할 것"에 있다.

### Payment — 9~11단계 ⬜

계약은 [payment.md](domains/purchase/payment.md).

## Phase 5 — 리뷰 ⬜

계약: [domains/review.md](domains/review.md).

## Phase 6 — 마무리 ⬜

- [ ] [architecture.md](architecture.md)의 RBAC 매트릭스와 실제 필터체인을 엔드포인트 단위로 대조
- [ ] 공개 조회 API를 permitAll로 여는 작업 — `/api/categories`는 Phase 3 4단계에서,
      `/api/products`(목록·단건)는 8단계에서 처리했다. 남은 것은 리뷰 목록이며,
      그 전까지는 `anyRequest().authenticated()`가 막는다
- [x] ADMIN 권한 체크 도입 시 `AccessDeniedHandler` 재검토 — Phase 3에서 완료.
      [ADR 0010](decisions/platform/0010-access-denied-handler-for-authorization.md)이
      [ADR 0007](decisions/platform/0007-single-entrypoint-for-401-403.md)을 대체한다

테스트는 별도 Phase로 미루지 않는다 — **각 도메인 구현과 함께 작성한다.** Phase 2가 그렇게 진행됐다.

## Phase 7 — 배포 ⬜

[deployment.md](deployment.md) 참고.

## 미결 사항

결정되면 [decisions/](decisions/)에 ADR로 남기고 여기서 지운다.

- [ ] **상품 옵션(사이즈·색상)** — Phase 4는 옵션 없이 `상품 : 수량`으로 간다
      ([ADR 0018](decisions/purchase/0018-no-product-options-yet.md)). 도입하려면 재고를
      상품 단위(`Product.stockQuantity`)에서 옵션 단위로 내릴지 함께 정해야 하고, 그때
      [ADR 0015](decisions/catalog/0015-no-stock-quantity-in-public-response.md)의 전제
      (`stockQuantity == 0` ↔ `status == SOLD_OUT`)가 깨지므로 그 ADR을 대체해야 한다
- [ ] **ADMIN 계정 발급 방식** — Phase 6에서 결정. 현재는 운영자가 DB에서 직접 바꾼다
      (`UPDATE users SET role = 'ADMIN' WHERE id = ?`). 승격 API는 없고, 도메인 메서드만 열려 있다
- [ ] **PKCE 도입** — 보류 사유가 하나만 남았다.
      [ADR 0003](decisions/auth/0003-no-pkce-yet.md)
- [ ] **Refresh Token Rotation + 재사용 탐지** — 함께 도입해야 한다.
      [ADR 0004](decisions/auth/0004-no-refresh-token-rotation.md)
- [ ] **만료 `refresh_tokens` row 정리 배치** — `expiresAt` 검사로 거절되므로 기능상 무해
- [ ] **LOCAL 로그인** — `V{n}__add_password.sql`(n은 착수 시점의 다음 번호. V2~V4는 카테고리·상품·장바구니가 가져갔고,
      ADR 0001의 `V2__add_password.sql`은 그 전에 쓴 번호다) + `PasswordEncoder` + `Provider.LOCAL` +
      `AuthErrorCode`에 `INVALID_CREDENTIALS` 재추가.
      [ADR 0001](decisions/auth/0001-kakao-only-oauth.md)
- [ ] **재고 기반 수량 셀렉터 상한** — 상품 상세 응답에 구매 가능 최대 수량을 노출하면 FE가
      셀렉터를 그 값까지만 열 수 있다(무신사 방식). [ADR 0015](decisions/catalog/0015-no-stock-quantity-in-public-response.md)가 공개 응답에서 재고를 뺐으므로
      **대체 ADR이 필요하다.** 스키마·엔티티·서버 검증은 바뀌지 않고 응답 필드 추가로 끝난다 —
      0015가 "필드를 다시 넣는 것은 추가라 클라이언트를 깨지 않는다"고 적어둔 방향이다.
      ⚠️ 노출해도 조회 시점 값이라 `CT002`·`P002` 거절은 남는다. UX 개선이지 검증 대체가 아니다
- [ ] **외부 PG 연동** — 포트원 등. 현재 Mock 전제
- [ ] **Redis 도입** — Refresh Token 저장, 인기 상품 캐싱
- [ ] **쿠폰 / 포인트 도메인** 추가 여부
- [ ] **검색 고도화** — Elasticsearch 등

확정된 것:
- [x] 소셜 로그인은 **카카오 전용** — 구글 등 추가는 `Provider` enum 상수 추가로 확장
- [x] **상품 목록 동적 필터는 JPA Specification** — 복잡해지면 QueryDSL로 리팩터링.
      [ADR 0011](decisions/catalog/0011-jpa-specification-for-product-filter.md)
- [x] **상품·리뷰 이미지는 별도 테이블에 URL만** — 스토리지는 로컬 → 추후 AWS S3.
      [ADR 0012](decisions/catalog/0012-image-urls-in-separate-table.md)
- [x] **공개 상품 응답에 재고 수량을 담지 않는다** — 품절은 `status`가 표현한다.
      [ADR 0015](decisions/catalog/0015-no-stock-quantity-in-public-response.md)

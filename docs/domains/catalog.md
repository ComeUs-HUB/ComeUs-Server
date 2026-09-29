# category / product — 카테고리 · 상품

> 이 문서는 **계약**이고, 무엇이 끝났는지는 [roadmap.md](../roadmap.md)가 소유한다.
> **Category·Product 모두 조회와 ADMIN CRUD까지 코드에 있다.**

ADMIN 접근 제어는 Category 구현과 함께 들어왔다 — `hasRole("ADMIN")`이 `SecurityConfig`에 붙어 있고,
인가 실패는 A002(403)로 나간다
([ADR 0010](../decisions/0010-access-denied-handler-for-authorization.md)).
Product의 ADMIN CRUD는 같은 배선을 그대로 쓴다.

## Category

### 엔티티

id, name, parentCategory(self-join, nullable), displayOrder, createdAt

### API

| Method | URI | 설명 | 권한 | 성공 응답 |
|---|---|---|---|---|
| GET | `/api/categories` | 전체 카테고리 트리 조회 | **비회원** | 200 |
| POST | `/api/categories` | 카테고리 생성 | ADMIN | **201** + `Location` |
| PUT | `/api/categories/{id}` | 카테고리 수정 | ADMIN | 200 |
| DELETE | `/api/categories/{id}` | 카테고리 삭제 | ADMIN | 200 + `data: null` |

- 단건 조회 API는 **없다.** 트리 하나로 화면이 구성되므로 만들지 않았다
- `Location`은 절대 URL이 아니라 **상대 경로**(`/api/categories/1`)다.
  운영에서는 `nginx(https://api.example.com) → 앱(http://localhost:8080)` 구조라
  앱이 보는 주소가 내부 주소다 — 절대 URL을 만들면 그 값이 그대로 클라이언트에 나가 따라갈 수 없다.
  상대 경로면 받는 쪽이 자기가 요청한 주소를 기준으로 해석하므로(RFC 3986 §5) 프록시 구성과 무관해진다
  ([deployment.md](../deployment.md))
- DELETE가 204가 아닌 이유: 바디가 없으면 공통 `{success, data, error}` 포맷이 깨진다
  ([conventions/api-response.md](../conventions/api-response.md))

### 규칙

- **2depth 구조**(대분류 > 소분류)를 self-join으로 표현한다
- 하위 카테고리가 있으면 삭제할 수 없다 → `CA002 CATEGORY_HAS_CHILDREN`
- 전시 순서는 `displayOrder` 필드로 관리한다. 정렬은 `displayOrder` 오름차순, **동률이면 `id`**
- 삭제는 **하드 삭제**다. 상품(`HIDDEN` soft delete)과 다르다 — 카테고리는 주문 이력에
  매달리지 않아 지워도 과거 기록이 깨지지 않는다
- 수정은 **이름과 전시 순서만** 바꾼다. 부모 변경은 요청 DTO에 필드조차 없다
- 상품이 붙어 있으면 삭제할 수 없다 → `CA004 CATEGORY_HAS_PRODUCTS` (아래 Product 규칙 참고)

### 확정된 것 (착수 시 결정)

- **3단계 이상 계층은 두지 않는다.** self-join을 유지하되 깊이 제한은 **DB가 아니라
  `Category.child()` 정적 팩터리가 강제**한다 — self-join 자체는 3depth 이상도 구조적으로
  허용하기 때문이다. 위반은 `CA003 CATEGORY_DEPTH_EXCEEDED`(400)
- **트리 응답은 대분류 배열에 소분류를 중첩**한다. 소분류의 `children`은 **항상 빈 배열**이고,
  이 점이 2depth 계약을 응답 자체로 드러낸다. 조회는 **쿼리 1회** — 전부 읽어 메모리에서 묶는다

## Product

### 엔티티

id, name, description, price, stockQuantity, category,
status(ON_SALE/SOLD_OUT/HIDDEN), createdAt, updatedAt

이미지는 이 테이블에 두지 않는다 — `product_images`가 `product_id`로 참조하며 URL만 담는다
([ADR 0012](../decisions/0012-image-urls-in-separate-table.md)).

### API

| Method | URI | 설명 | 권한 | 성공 응답 |
|---|---|---|---|---|
| GET | `/api/products` | 목록 조회 (페이징·필터·정렬) | **비회원** | 200 + `PageResponse` |
| GET | `/api/products/{id}` | 상세 조회 | **비회원** | 200 |
| POST | `/api/products` | 등록 | ADMIN | **201** + `Location` |
| PUT | `/api/products/{id}` | 수정 | ADMIN | 200 |
| DELETE | `/api/products/{id}` | 삭제 (soft delete) | ADMIN | 200 + `data: null` |

- 목록 필터는 `categoryId` · 가격 범위 · 키워드를 **조합**할 수 있다
- `Location`은 카테고리와 같이 **상대 경로**(`/api/products/1`)다
- DELETE가 204가 아닌 이유도 카테고리와 같다 — 바디가 없으면 공통 포맷이 깨진다

### 규칙

- **품절 자동 전환은 엔티티 책임이다.** 재고를 바꾸는 도메인 메서드가 `status`까지 확정한다 —
  서비스가 재고만 바꾸고 `status`를 잊는 조합이 생기지 않는다. 재고를 만지는 경로가
  Phase 4(주문·취소·결제 실패)에서 늘어나므로, 판정이 호출자마다 반복되면 한 곳은 반드시 빠진다.
  `Category.child()`가 2depth를 생성 지점 하나로 강제하는 것과 같은 방식이다
- 재고 0 → status 자동 `SOLD_OUT`. 재고 부족은 `P002 OUT_OF_STOCK`
- ⚠️ **재고가 다시 차도 `SOLD_OUT`이었을 때만 `ON_SALE`로 복귀한다.** `HIDDEN`은 재고와 다른 축이라
  복귀 대상이 아니다 — 조건 없이 되돌리면 삭제된 상품이 재고 복구만으로 목록에 다시 뜬다
  ([ADR 0013](../decisions/0013-hide-soft-deleted-products-everywhere.md))
- **공개 응답은 재고 수량을 담지 않는다.** 목록·상세 모두 `stockQuantity`가 없고,
  구매 가능 여부는 `status`가 표현한다(`SOLD_OUT`). 조회가 비회원에게 열려 있어
  재고를 내보내면 폴링만으로 판매량이 추정된다 ([ADR 0015](../decisions/0015-no-stock-quantity-in-public-response.md))
- 삭제는 **soft delete**(status = `HIDDEN`) — 주문 이력에 연결된 상품이 사라지면 안 된다.
  카테고리의 하드 삭제와 갈리는 지점이다
- **`HIDDEN`은 공개 조회 API 전부에서 숨긴다.** 목록에서 제외하고 상세는 `P001`(404)다.
  권한으로 분기하지 않아 ADMIN도 같은 결과를 받는다.
  다만 수정·삭제가 쓰는 조회에는 필터를 걸지 않는다 — 그것이 되살리는 유일한 통로다
  ([ADR 0013](../decisions/0013-hide-soft-deleted-products-everywhere.md))
- **`categoryId` 필터는 대분류를 지정하면 그 아래 소분류 상품까지 포함한다.**
  `IN (자기 자신 + 자식들)`로 조립하며, 2depth 고정이라 재귀가 없다.
  자식 id는 `CategoryService`를 경유해 얻는다 — `CategoryRepository`는 category 패키지 밖에서
  주입하지 않는다
- 상품이 붙은 카테고리는 삭제할 수 없다 → `CA004 CATEGORY_HAS_PRODUCTS`.
  이 검사는 `CategoryService`가 `ProductRepository`를 직접 읽어 수행한다
  ([ADR 0014](../decisions/0014-category-reads-product-repository.md))
- 목록 조회는 `PageResponse.of()`를 그대로 재사용한다
  ([conventions/api-response.md](../conventions/api-response.md)).
  **이 도메인이 `PageResponse`의 첫 실사용처가 된다**

### 확정된 것

- **동적 필터는 JPA Specification으로 시작한다.** 조건이 복잡해지면 QueryDSL로 리팩터링하며,
  전환 신호를 ADR에 적어 뒀다 — [ADR 0011](../decisions/0011-jpa-specification-for-product-filter.md).
  Specification 조립은 한곳에 모은다(서비스로 흩뿌리지 않는다) — 그것이 전환 비용을 정한다
- **이미지는 `product_images` 별도 테이블에 URL만 저장한다.** 스토리지는 로컬로 시작해
  추후 AWS S3로 옮기며, URL만 저장하므로 전환에 스키마 변경이 없다 —
  [ADR 0012](../decisions/0012-image-urls-in-separate-table.md).
  리뷰 이미지도 **같은 방식**이다([review.md](review.md))
- **페이징 기본값은 `size=20`, 정렬은 `createdAt` 내림차순.** 최대 `size`는 **100**이며 초과하면
  잘라낸다. `api-response.md`가 "기본값과 최대 `size` 제한은 도메인별로 정한다"고 위임한 값이다
- **정렬 필드는 `createdAt` · `price` · `name` 셋으로 제한한다.** 위반은 `C001`(400)이다.
  ⚠️ 화이트리스트가 없으면 `?sort=foo`가 `PropertyReferenceException`을 일으키고,
  이 예외는 `GlobalExceptionHandler`가 처리하는 7종에 없어 **`C003`(500)으로 나간다** —
  클라이언트 입력 오류가 서버 오류로 보고된다
- **재고 수량은 공개 응답에서 뺀다.** 품절은 `status` 하나로 표현하며, 관리자용 재고 노출이
  필요해지면 쓰기 응답 DTO를 분리하는 쪽으로 확장한다 —
  [ADR 0015](../decisions/0015-no-stock-quantity-in-public-response.md)
- **응답 DTO는 목록용과 상세용을 나눈다.** 목록은 대표 이미지(`displayOrder` 최소) 한 장만 담고,
  상세가 이미지 전체와 `description`을 담는다. `CategoryResponse`/`CategoryTreeResponse`를
  나눈 것과 같은 이유다

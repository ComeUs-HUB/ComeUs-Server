# category / product — 카테고리 · 상품

> 이 문서는 **계약**이고, 무엇이 끝났는지는 [roadmap.md](../roadmap.md)가 소유한다.
> **Category는 구현돼 있고 Product는 아직 `ProductErrorCode`만 선반영돼 있다.**

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
- `Location`은 절대 URL이 아니라 **상대 경로**(`/api/categories/1`)다 — 프록시 뒤에서
  호스트·스킴이 어긋나지 않게 하기 위함이다
- DELETE가 204가 아닌 이유: 바디가 없으면 공통 `{success, data, error}` 포맷이 깨진다
  ([conventions/api-response.md](../conventions/api-response.md))

### 규칙

- **2depth 구조**(대분류 > 소분류)를 self-join으로 표현한다
- 하위 카테고리가 있으면 삭제할 수 없다 → `CA002 CATEGORY_HAS_CHILDREN`
- 전시 순서는 `displayOrder` 필드로 관리한다. 정렬은 `displayOrder` 오름차순, **동률이면 `id`**
- 삭제는 **하드 삭제**다. 상품(`HIDDEN` soft delete)과 다르다 — 카테고리는 주문 이력에
  매달리지 않아 지워도 과거 기록이 깨지지 않는다
- 수정은 **이름과 전시 순서만** 바꾼다. 부모 변경은 요청 DTO에 필드조차 없다
- ⚠️ **"상품이 붙은 카테고리 삭제"는 아직 막지 못한다.** `Product`가 없어 FK 자체가 없다 —
  `products.category_id`가 생기는 시점에 함께 막아야 한다

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

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/products` | 목록 조회 (페이징·필터·정렬) | 비회원 |
| GET | `/api/products/{id}` | 상세 조회 | 비회원 |
| POST | `/api/products` | 등록 | ADMIN |
| PUT | `/api/products/{id}` | 수정 | ADMIN |
| DELETE | `/api/products/{id}` | 삭제 | ADMIN |

### 규칙

- 재고 0 → status 자동 `SOLD_OUT`. 재고가 다시 차면 `ON_SALE`로 복귀
- 삭제는 **soft delete**(status = `HIDDEN`) — 주문 이력에 연결된 상품이 사라지면 안 된다
- 재고 부족은 `P002 OUT_OF_STOCK`
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

### 착수 전 정할 것

- 품절 자동 전환을 엔티티 책임으로 둘지 서비스 책임으로 둘지

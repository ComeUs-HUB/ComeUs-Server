# category / product — 카테고리 · 상품

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../roadmap.md)가 소유한다.
> `CategoryErrorCode`·`ProductErrorCode`만 선반영돼 있다.

ADMIN 권한 체계가 확정된 뒤에 착수한다 — CRUD 접근 제어를 걸 수 있어야 하기 때문이다.

## Category

### 엔티티

id, name, parentCategory(self-join, nullable), displayOrder, createdAt

### API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/categories` | 전체 카테고리 트리 조회 | 비회원 |
| POST | `/api/categories` | 카테고리 생성 | ADMIN |
| PUT | `/api/categories/{id}` | 카테고리 수정 | ADMIN |
| DELETE | `/api/categories/{id}` | 카테고리 삭제 | ADMIN |

### 규칙

- **2depth 구조**(대분류 > 소분류)를 self-join으로 표현한다
- 하위 카테고리가 있으면 삭제할 수 없다 → `CA002 CATEGORY_HAS_CHILDREN`
- 전시 순서는 `displayOrder` 필드로 관리한다

### 착수 전 정할 것

- 3단계 이상 계층이 필요해질 가능성이 있는가 — 있다면 self-join 대신 다른 구조를 검토
- 트리 조회 응답 형태(대분류 하위에 소분류 배열을 중첩하는 구조 등)를 FE와 사전 합의

## Product

### 엔티티

id, name, description, price, stockQuantity, category, imageUrls,
status(ON_SALE/SOLD_OUT/HIDDEN), createdAt, updatedAt

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

### 착수 전 정할 것

- 필터(카테고리·가격 범위·키워드) 조합을 어떤 방식으로 구현할지 — QueryDSL, JPA Specification 등.
  조건이 늘어날 것을 전제로 고른다
- 품절 자동 전환을 엔티티 책임으로 둘지 서비스 책임으로 둘지
- 이미지 저장 방식(로컬 vs 외부 스토리지) — 우선 URL 목록만 저장하는 구조로 시작할지.
  [review.md](review.md)의 리뷰 이미지와 **같은 방식으로 정한다**

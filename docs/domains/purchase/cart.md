# cart — 장바구니

> 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.

구매 플로우의 입구. 담긴 항목을 주문으로 옮기는 것은 [order.md](order.md)이며 **장바구니는 주문을
모른다.** 셋에 걸친 엔티티 관계는 [README.md](README.md)가 소유한다.

## 엔티티

id, user, cartItems[ { product, quantity } ]

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/cart` | 내 장바구니 조회 | 회원 |
| POST | `/api/cart/items` | 상품 담기 | 회원 |
| PUT | `/api/cart/items/{itemId}` | 수량 변경 | 회원 |
| DELETE | `/api/cart/items/{itemId}` | 항목 삭제 | 회원 |
| DELETE | `/api/cart` | 전체 비우기 | 회원 |

## 규칙

- 회원 1명당 장바구니 1개 (1:1)
- **"동일 항목"은 상품ID로 판단한다.** 옵션(사이즈·색상)은 초기 범위 밖이다 — [ADR 0018](../../decisions/purchase/0018-no-product-options-yet.md)
- 동일 항목 재담기 시 수량 합산
- 재고 초과 수량은 담을 수 없다 → `CT002 CART_QUANTITY_EXCEEDED`.
  **검사 대상은 합산 후 수량(기존 + 요청)이다** — 1개씩 나눠 담아 재고를 넘기는 경로를 막는다
- **상품 종류는 50종까지다.** 초과하면 `CT003 CART_ITEM_LIMIT_EXCEEDED`.
  합산은 종류를 늘리지 않으므로 **새 항목이 생길 때만** 걸린다.
  상한이 없으면 조회 응답·주문 입력·전체 비우기([ADR 0019](../../decisions/purchase/0019-cart-owns-item-deletion.md))의 크기가 무제한이 된다
- **새 상품 담기는 `CT003`을 `CT002`보다 먼저 판정한다.** 담을 자리가 없으면 수량을 보지 않는다

## 담기 검사는 재고를 점유하지 않는다

담기 시점에 재고를 검사하지만 **차감하지도 예약하지도 않는다.** 담기 검사(`CT002`)는 UX용 선행
거절이고 보장이 아니다 — 담은 뒤 품절될 수 있고, 그때는 주문 생성의 `P002`가 거절한다.
**재고 점유는 결제 대기(주문 `PENDING`)에서 일어난다.** 근거와 배제한 대안은 [ADR 0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md).

## 조회 응답

- 항목별 **구매 가능 여부를 불리언으로** 담는다. 재고 수량은 담지 않는다 — [ADR 0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md)
  - **`ON_SALE`이고 수량이 재고 이하일 때만 `true`다** — 지금 이 수량으로 주문이 통과하는지와 같다.
    재고 부족·품절·숨김은 모두 `false`이고, 이유를 구분하는 값은 필요해지면 필드로 더한다
- **서버가 수량을 자동 조정하지 않는다.** 사용자가 넣은 값은 그대로 두고 주문 시점에 거절한다.
  숨김 상품이 된 항목도 응답에서 빼지 않는다 — 빼면 지울 수 없는 항목이 상한을 차지한다
- 합계는 **구매 불가 항목까지 포함한** 현재가 기준이다

## 확정된 것 (착수 시 결정)

- **장바구니 행은 첫 담기에서 만든다(lazy).** 가입 시 빈 행을 만들지 않고, `GET /api/cart`는
  장바구니가 없으면 **빈 응답**을 준다 — 조회가 쓰기를 하지 않는다
- **담기·수량 변경·항목 삭제·전체 비우기의 응답은 전체 장바구니다.** 합산 때문에 바뀌는 범위가 항목 하나로
  끝나지 않으므로, 클라이언트가 재조회할 이유를 없앤다. 비우기는 빈 장바구니를 돌려준다
- **같은 회원의 쓰기는 장바구니 행을 잠가 직렬화한다.** 행이 없으면 잠그지 않으며, 동시 첫 담기는
  `uk_carts_user_id` 위반 → `C005`(409)로 끝난다 — 없는 행을 잠그면 gap lock끼리 교착한다
- **`POST /api/cart/items`는 201이 아니라 200이다.** 합산이면 생성이 아니라 수정이라
  `Location`으로 가리킬 자원이 정해지지 않는다
- **타인 항목 접근은 403이 아니라 404(`CT001`)다.** 존재 자체를 숨긴다 — soft delete된 상품을
  숨기는 것과 같은 판단이다([ADR 0013](../../decisions/catalog/0013-hide-soft-deleted-products-everywhere.md))
- **`PUT`에 `quantity: 0`은 `C001`로 거절한다.** 삭제로 해석하지 않는다 — `DELETE`가 이미 있다
- **`HIDDEN` 상품은 담을 수 없다 → `P001`(404).** 공개 조회에서 숨기는 상품이 장바구니로는
  들어오는 구멍을 막는다([ADR 0013](../../decisions/catalog/0013-hide-soft-deleted-products-everywhere.md))
- **응답 DTO는 장바구니 전체와 항목으로 나눈다.** 항목은 구매 가능 여부 불리언을 담고 재고
  수량은 담지 않는다([ADR 0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md)). 전체는 수량 합계와 **현재가 합계**를 담는다 — 스냅샷이 아니다.
  스냅샷은 주문의 `priceAtOrder`가 가진다([order.md](order.md))

## 주문에 제공하는 것

[order.md](order.md)의 주문 생성이 쓰는 것은 둘뿐이다. 장바구니는 주문을 모르고,
이 둘의 소유자는 cart다([ADR 0017](../../decisions/purchase/0017-order-copies-cart-and-cuts-the-link.md)).

| 용도 | 비고 |
|---|---|
| 선택한 항목 조회 | 소유자 검증을 포함한다. 타인 항목이거나 없으면 `CT001` |
| 선택한 항목 삭제 | 주문이 성립한 뒤. 선택하지 않은 항목은 남는다 |

조회·취소는 장바구니를 거치지 않는다.

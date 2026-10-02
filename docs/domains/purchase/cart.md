# cart — 장바구니

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `CartErrorCode`만 선반영돼 있다.

구매 플로우의 입구. 담긴 항목을 주문으로 옮기는 것은 [order.md](order.md)이며 **장바구니는 주문을
모른다.** 셋에 걸친 엔티티 관계는 [purchase.md](README.md)가 소유한다.

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

## 담기 검사는 재고를 점유하지 않는다

담기 시점에 재고를 검사하지만 **차감하지도 예약하지도 않는다.** 담기 검사(`CT002`)는 UX용 선행
거절이고 보장이 아니다 — 담은 뒤 품절될 수 있고, 그때는 주문 생성의 `P002`가 거절한다.
**재고 점유는 결제 대기(주문 `PENDING`)에서 일어난다.** 근거와 배제한 대안은 [ADR 0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md).

## 조회 응답

- 항목별 **구매 가능 여부를 불리언으로** 담는다. 재고 수량은 담지 않는다 — [ADR 0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md)
- **서버가 수량을 자동 조정하지 않는다.** 사용자가 넣은 값은 그대로 두고 주문 시점에 거절한다

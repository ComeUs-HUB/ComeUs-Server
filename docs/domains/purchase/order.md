# order — 주문

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `OrderErrorCode`만 선반영돼 있다.

[cart.md](cart.md)의 항목을 값으로 복사해 성립하고, 결제는 [payment.md](payment.md)가 잇는다.
셋에 걸친 엔티티 관계는 [README.md](README.md)가 소유한다.

## 엔티티

id, user, orderItems[ { product, quantity, priceAtOrder } ], totalAmount,
status(PENDING/PAID/SHIPPING/DELIVERED/CANCELLED), shippingAddress, createdAt

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/orders` | 주문 생성 | 회원 |
| GET | `/api/orders` | 내 주문 목록 | 회원 |
| GET | `/api/orders/{id}` | 주문 상세 | 회원 |
| POST | `/api/orders/{id}/cancel` | 주문 취소 | 회원 |
| GET | `/api/admin/orders` | 전체 주문 목록 | ADMIN |
| PUT | `/api/admin/orders/{id}/status` | 주문 상태 변경 | ADMIN |

## 규칙

- **입력은 선택한 `cartItemId` 목록이다.** 장바구니 전체가 아니라 사용자가 고른 항목만 주문한다
  - 주문이 성립하면 **그 항목만** 장바구니에서 지운다. 선택하지 않은 항목은 남는다
  - 타인의 항목이거나 존재하지 않는 id → `CT001 CART_ITEM_NOT_FOUND`
  - 빈 목록 → `C001 INVALID_INPUT_VALUE`
  - **장바구니를 거치지 않는 "바로 구매"는 초기 범위 밖이다** — 입력이 장바구니에 묶인다([ADR 0017](../../decisions/purchase/0017-order-copies-cart-and-cuts-the-link.md))
- 주문 생성 시 재고 차감. **동시에 마지막 재고를 두고 경쟁해도 음수로 내려가면 안 된다**
- **재고 점유는 결제 대기에서 일어난다.** 주문 생성(`PENDING`)의 차감이 곧 점유이고,
  결제 성공이 확정(`PAID`)한다. 장바구니는 점유하지 않는다 — [ADR 0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md)
- **`cartItem` 삭제와 재고 차감은 한 트랜잭션이다.** 재고 부족(`P002`)으로 주문이 실패하면
  장바구니는 그대로 남아야 한다 — 거절당했는데 장바구니까지 비는 것이 최악이다
- 주문 시점 가격을 `priceAtOrder`에 **스냅샷** 저장 — 이후 상품 가격이 바뀌어도 이미 성립한 주문의
  금액은 유지되어야 한다
- **`CANCELLED`로 가는 모든 전환이 재고를 복구한다.** 점유를 시작한 것이 주문 생성이므로
  결제 전(`PENDING`)이든 후(`PAID`)든, 회원이 취소하든 ADMIN이 바꾸든 같다.
  환불과 `PENDING` 만료에 따른 취소도 이 규칙에 흡수된다
- **결제창 단계의 실패·취소는 주문을 바꾸지 않고, 승인 거절은 주문을 `PENDING`으로 되돌린다.**
  둘 다 재고를 점유한 채 같은 주문으로 결제를 다시 시도한다([payment.md](payment.md))
- 재고 동시성 제어는 [ADR 0025](../../decisions/purchase/0025-pessimistic-lock-as-stock-concurrency-baseline.md),
  중복 주문 방어는 [ADR 0024](../../decisions/purchase/0024-order-idempotency-key-in-header.md)가 정한다
- SHIPPING 이후 회원이 직접 취소 불가 → `O002 ORDER_CANCEL_NOT_ALLOWED`

## 착수 전 정할 것

- **`PENDING` 주문의 만료 처리** — 결제되지 않은 주문이 재고를 계속 점유한다. 장바구니 예약을
  배제한 이유(담아두고 사지 않으면 재고가 묶인다)가 범위만 좁혀 그대로 재현된다.
  자동 취소 배치를 둘지, 사용자·ADMIN 취소에만 맡길지 정한다.
  결제창에서 실패·취소하거나 승인이 거절된 주문도 `PENDING`으로 남으므로 이 처리의 대상이다
- 배송지도 주문 시점 스냅샷이 필요한지
- 상태 전환표(각 상태에서 허용되는 다음 상태)를 확정

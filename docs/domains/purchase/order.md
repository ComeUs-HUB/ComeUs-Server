# order — 주문

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `OrderErrorCode`만 선반영돼 있다.

[cart.md](cart.md)의 항목을 값으로 복사해 성립하고, 결제는 [payment.md](payment.md)가 잇는다.
셋에 걸친 엔티티 관계는 [README.md](README.md)가 소유한다.

## 엔티티

id, user, orderNumber, idempotencyKey, paymentKey, orderItems[ { product, quantity, priceAtOrder } ], totalAmount,
status(PENDING/PAYMENT_IN_PROGRESS/PAID/SHIPPING/DELIVERED/CANCELLED), shippingAddress, createdAt, updatedAt

- `orderNumber` — 서버가 발급하고 토스 `orderId`로 쓴다. unique
- `paymentKey` — 승인을 선점한 시도의 키. 승인 거절로 `PENDING`에 돌아오면 비운다

## 상태 흐름

```
PENDING ──승인 선점──▶ PAYMENT_IN_PROGRESS ──승인 성공──▶ PAID ──▶ SHIPPING ──▶ DELIVERED
   ▲                        │                           │
   └───── 승인 거절 ──────────┘                           │
   │                                                    │
   └───── 취소·만료 ──▶ CANCELLED   ◀──── 취소·환불 ────────┘
```

- `PENDING`의 차감이 재고 점유이고 `PAID`가 확정한다. 장바구니는 점유하지 않는다 — [ADR 0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md)
- **`CANCELLED`로 가는 모든 전환이 재고를 복구한다.** 결제 전이든 후든, 회원이 취소하든 ADMIN이 바꾸든,
  환불이든 `PENDING` 만료든 같다
- 결제가 일으키는 전이는 [payment.md](payment.md)와 [ADR 0026](../../decisions/purchase/0026-payment-confirm-outside-transaction.md)이 정한다.
  결제창 단계의 실패·취소는 주문을 바꾸지 않고, 승인 거절은 `PENDING`으로 되돌린다.
  둘 다 재고를 점유한 채 같은 주문으로 결제를 다시 시도한다

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/orders` | 주문 생성 | 회원 |
| GET | `/api/orders` | 내 주문 목록 | 회원 |
| GET | `/api/orders/{id}` | 주문 상세 | 회원 |
| POST | `/api/orders/{id}/cancel` | 주문 취소 | 회원 |
| GET | `/api/admin/orders` | 전체 주문 목록 | ADMIN |
| PUT | `/api/admin/orders/{id}/status` | 주문 상태 변경 | ADMIN |

### POST `/api/orders` — 주문 생성

```
Idempotency-Key: {uuid}
{ cartItemIds, shippingAddress }

Controller   키 형식, 빈 목록 검증                        → C001

트랜잭션 시작 (READ_COMMITTED)
  1. 장바구니 존재 확인 (락 없음)                         → 없으면 CT001
  2. carts 행 잠금
  3. (user_id, 멱등키)로 주문 조회                        → 있으면 그 주문을 반환하고 끝
  4. 선택한 cartItem 조회, 소유자 검증                    → 타인·없는 항목 CT001
  5. products 행 잠금 (id 오름차순)
  6. 재고 차감 Product.decreaseStock()                    → 부족 P002
  7. Order(PENDING) 저장, priceAtOrder 스냅샷
  8. 선택한 cartItem 삭제
커밋

응답   { orderNumber, amount }
```

장바구니에서 고른 항목을 주문으로 복사하면서 재고를 점유한다. 응답의 두 값으로 FE가 토스 결제창을 연다.

**입력**
- 장바구니 전체가 아니라 **선택한 `cartItemId` 목록**이다
- **장바구니를 거치지 않는 "바로 구매"는 초기 범위 밖이다** — [ADR 0017](../../decisions/purchase/0017-order-copies-cart-and-cuts-the-link.md)

**중복 요청**
- 같은 멱등키의 재요청은 거절하지 않고 **기존 주문을 반환**하며 재고를 건드리지 않는다
- 3번을 우회해 uk 위반이 나면 롤백 후 별도 트랜잭션에서 기존 주문을 읽어 반환한다 — [ADR 0024](../../decisions/purchase/0024-order-idempotency-key-in-header.md)

**재고**
- 동시에 마지막 재고를 두고 경쟁해도 **음수로 내려가면 안 된다** — [ADR 0025](../../decisions/purchase/0025-pessimistic-lock-as-stock-concurrency-baseline.md)

**스냅샷**
- `priceAtOrder`에 주문 시점 가격을 저장한다. 이후 상품 가격이 바뀌어도 성립한 주문의 금액은 유지된다

**실패 시 장바구니**
- 6~8번은 한 트랜잭션이다. `P002`로 실패하면 장바구니는 그대로 남는다 — 거절당했는데 장바구니까지 비는 것이 최악이다
- 주문이 성립하면 **선택한 항목만** 지운다. 선택하지 않은 항목은 남는다

### GET `/api/orders` — 내 주문 목록

```
1. 로그인 회원의 주문 조회
2. 응답 변환
```

자기 주문만 돌려준다. 재고와 장바구니를 건드리지 않는다.

### GET `/api/orders/{id}` — 주문 상세

```
1. id로 주문 조회                    → 없으면 O001
2. 소유자 검증                       → 타인의 주문 O001
3. 주문 항목과 함께 응답 변환
```

금액은 `priceAtOrder` 스냅샷으로 보여 준다. 타인의 주문은 존재 여부를 드러내지 않도록 없는 주문과 같은 `O001`로 응답한다.

### POST `/api/orders/{id}/cancel` — 주문 취소

```
1. id로 주문 조회                    → 없으면 O001
2. 소유자 검증                       → 타인의 주문 O001
3. 현재 상태로 분기
   PENDING               CAS → CANCELLED, products 잠금 (id 오름차순), 재고 복구
   PAYMENT_IN_PROGRESS   거절
   PAID                  환불 후 CANCELLED, 재고 복구
   SHIPPING, DELIVERED   거절 O002
```

회원이 자기 주문을 취소하고 점유했던 재고를 돌려놓는다.

- **결제 승인 중인 주문은 취소할 수 없다** — 토스 승인과 어긋날 수 있다. [ADR 0026](../../decisions/purchase/0026-payment-confirm-outside-transaction.md)
- `PENDING` 취소는 CAS로 한다. 그사이 승인이 선점했다면 갱신 0건이므로 취소하지 않는다
- `SHIPPING` 이후에는 회원이 직접 취소할 수 없다 → `O002 ORDER_CANCEL_NOT_ALLOWED`
- `PAID` 취소의 갱신 순서는 [payment.md](payment.md)의 환불 순서를 따른다

### GET `/api/admin/orders` — 전체 주문 목록

```
1. ADMIN 권한 확인                   → 아니면 A002
2. 전체 주문 조회
3. 응답 변환
```

회원 구분 없이 모든 주문을 돌려준다.

### PUT `/api/admin/orders/{id}/status` — 주문 상태 변경

```
1. ADMIN 권한 확인                   → 아니면 A002
2. id로 주문 조회                    → 없으면 O001
3. 상태 전환표로 허용 여부 확인
4. 상태 변경
5. CANCELLED로 바꿨다면 재고 복구
```

ADMIN이 배송 처리처럼 주문 상태를 직접 바꾼다.

- 허용되는 전환은 아래 "착수 전 정할 것"의 상태 전환표가 정한다
- **결제 승인 중인 주문은 ADMIN도 취소할 수 없다**
- ADMIN이 `CANCELLED`로 바꿔도 재고를 복구한다

## 착수 전 정할 것

- **`PENDING` 주문의 만료 처리** — 결제되지 않은 주문이 재고를 계속 점유한다. 장바구니 예약을
  배제한 이유(담아두고 사지 않으면 재고가 묶인다)가 범위만 좁혀 그대로 재현된다.
  자동 취소 배치를 둘지, 사용자·ADMIN 취소에만 맡길지 정한다.
  결제창에서 실패·취소하거나 승인이 거절된 주문도 `PENDING`으로 남으므로 이 처리의 대상이다
- 배송지도 주문 시점 스냅샷이 필요한지
- 상태 전환표(각 상태에서 허용되는 다음 상태)를 확정
- 목록 API의 페이지네이션·정렬
- 승인 진행 중 취소 거절과 이미 취소된 주문의 재취소를 어떤 코드로 응답할지 — `O002` 메시지는 배송 중만 덮는다
- 주문 시점에 숨김(`HIDDEN`)·판매 중지된 상품을 어떻게 처리할지

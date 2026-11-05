# order — 주문

> 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> 엔티티와 스키마까지 코드에 있다.

[cart.md](cart.md)의 항목을 값으로 복사해 성립하고, 결제는 [payment.md](payment.md)가 잇는다.
셋에 걸친 엔티티 관계는 [README.md](README.md)가 소유한다.

## 엔티티

id, user, orderNumber, idempotencyKey, paymentKey, orderItems[ { product, quantity, priceAtOrder } ], totalAmount,
status(PENDING/PAYMENT_IN_PROGRESS/PAID/SHIPPING/DELIVERED/CANCELLED), shippingAddress, createdAt, updatedAt

- `orderNumber` — 서버가 발급하고 토스 `orderId`로 쓴다. unique
- `paymentKey` — 승인을 선점한 시도의 키. 승인 거절로 `PENDING`에 돌아오면 비운다
- `shippingAddress` — `@Embedded`(recipientName, recipientPhone, address). 전부 필수이고 주문 시점 값을 **스냅샷**한다.
  회원 정보가 바뀌어도 성립한 주문의 배송지는 그대로다

## 상태 흐름

```
PENDING ──승인 선점──▶ PAYMENT_IN_PROGRESS ──승인 성공──▶ PAID ──▶ SHIPPING ──▶ DELIVERED
   ▲                        │                           │
   └───── 승인 거절 ──────────┘                           │
   │                                                    │
   └───── 취소·만료 ──▶ CANCELLED   ◀──── 취소·환불 ────────┘
```

| 현재 상태 | 다음 상태 | 언제 |
|---|---|---|
| `PENDING` | `PAYMENT_IN_PROGRESS` | 결제 승인 선점 |
| `PENDING` | `CANCELLED` | 회원·ADMIN 취소, 만료 |
| `PAYMENT_IN_PROGRESS` | `PAID` | 승인 성공 |
| `PAYMENT_IN_PROGRESS` | `PENDING` | 승인 거절 |
| `PAID` | `SHIPPING` | ADMIN 배송 처리 |
| `PAID` | `CANCELLED` | 회원·ADMIN의 환불 후 취소 |
| `SHIPPING` | `DELIVERED` | ADMIN 배송 완료 처리 |

`DELIVERED`와 `CANCELLED`는 최종 상태다. **표에 없는 전이는 모두 거절한다.**

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
  6. 숨김 상품 확인                                       → HIDDEN P001
  7. 재고 차감 Product.decreaseStock()                    → 부족·품절 P002
  8. Order(PENDING) 저장, priceAtOrder·배송지 스냅샷
  9. 선택한 cartItem 삭제
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
- 장바구니에 담은 뒤 숨겨진 상품은 주문하지 않는다. `decreaseStock()`은 숨김을 거르지 않으므로 잠금 뒤 상태로 확인한다

**스냅샷**
- `priceAtOrder`에 주문 시점 가격을 저장한다. 이후 상품 가격이 바뀌어도 성립한 주문의 금액은 유지된다
- 배송지가 비어 있으면 `C001`이다

**실패 시 장바구니**
- 7~9번은 한 트랜잭션이다. `P002`로 실패하면 장바구니는 그대로 남는다 — 거절당했는데 장바구니까지 비는 것이 최악이다
- 주문이 성립하면 **선택한 항목만** 지운다. 선택하지 않은 항목은 남는다

### GET `/api/orders` — 내 주문 목록

```
1. 로그인 회원의 주문 조회 (페이징)
2. PageResponse 변환
```

자기 주문만 돌려준다. 재고와 장바구니를 건드리지 않는다.
페이징은 [product.md](../catalog/product.md)와 같다 — 기본 `size=20`, 최대 100, `createdAt` 내림차순.

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
   PAYMENT_IN_PROGRESS   거절 O003
   PAID                  환불 후 CANCELLED, 재고 복구
   SHIPPING, DELIVERED   거절 O002
   CANCELLED             거절 O004
```

회원이 자기 주문을 취소하고 점유했던 재고를 돌려놓는다.

- **결제 승인 중인 주문은 취소할 수 없다** — 토스 승인과 어긋날 수 있다. [ADR 0026](../../decisions/purchase/0026-payment-confirm-outside-transaction.md)
- `PENDING` 취소는 CAS로 한다. 그사이 승인이 선점했다면 갱신 0건이므로 취소하지 않는다
- `SHIPPING` 이후에는 회원이 직접 취소할 수 없다 → `O002 ORDER_CANCEL_NOT_ALLOWED`
- `PAID` 취소의 갱신 순서는 [payment.md](payment.md)의 환불 순서를 따른다

### GET `/api/admin/orders` — 전체 주문 목록

```
1. ADMIN 권한 확인                   → 아니면 A002
2. 전체 주문 조회 (페이징)
3. PageResponse 변환
```

회원 구분 없이 모든 주문을 돌려준다. 페이징은 내 주문 목록과 같다.

### PUT `/api/admin/orders/{id}/status` — 주문 상태 변경

```
1. ADMIN 권한 확인                   → 아니면 A002
2. id로 주문 조회                    → 없으면 O001
3. 상태 전환표로 허용 여부 확인      → 승인 진행 중 취소 O003, 그 밖의 불허 전이 O005
4. 다음 상태로 분기
   CANCELLED (PENDING에서)   CAS → CANCELLED, products 잠금 (id 오름차순), 재고 복구
   CANCELLED (PAID에서)      환불 후 CANCELLED, 재고 복구
   그 밖의 상태              CAS로 상태 변경
```

ADMIN이 배송 처리처럼 주문 상태를 직접 바꾼다.

- 허용되는 전환은 위 상태 전환표가 정한다. ADMIN은 `SHIPPING`·`DELIVERED` 주문을 취소할 수 없다
- **결제 승인 중인 주문은 ADMIN도 취소할 수 없다**
- ADMIN도 `PENDING`·`PAID` 주문을 `CANCELLED`로 바꿀 수 있고 재고를 복구한다
- `PAID` 취소의 갱신 순서는 [payment.md](payment.md)의 환불 순서를 따른다

## `PENDING` 만료 배치

API가 아니라 스케줄 작업이다.

```
1. updatedAt이 15분 넘게 지난 PENDING 주문 조회
2. 주문마다 트랜잭션
   CAS PENDING → CANCELLED        → 0건이면 건너뜀 (그사이 승인 선점·취소)
   products 잠금 (id 오름차순), 재고 복구
```

결제되지 않은 주문이 재고를 계속 점유하지 않도록 **15분** 뒤 자동으로 취소한다.
토스 승인 유효 시간 10분에 여유를 더한 값으로, 결제창에서 정상 진행 중인 주문이 만료되지 않게 한다.
기준은 마지막으로 `PENDING`이 된 시각이다. 승인이 거절되면 그 시점부터 다시 15분을 준다.
결제창에서 실패·취소하거나 승인이 거절된 주문도 `PENDING`이므로 대상이다.

## 에러 코드

구현 때 `OrderErrorCode`와 [error-codes.md](../../conventions/error-codes.md)에 추가한다.

| 코드 | 상수 | HTTP | 언제 |
|---|---|---|---|
| O003 | `ORDER_PAYMENT_IN_PROGRESS` | 409 | 결제 승인 중인 주문의 취소 |
| O004 | `ORDER_ALREADY_CANCELLED` | 409 | 이미 취소된 주문의 재취소 |
| O005 | `INVALID_ORDER_STATUS_TRANSITION` | 409 | 상태 전환표에 없는 ADMIN 변경 |

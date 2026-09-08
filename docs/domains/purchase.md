# cart / order / payment — 구매 플로우

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../roadmap.md)가 소유한다.
> `CartErrorCode`·`OrderErrorCode`·`PaymentErrorCode`만 선반영돼 있다.

장바구니 → 주문 → 결제로 이어지는 핵심 흐름. [auth.md](auth.md)(로그인 사용자)와
[catalog.md](catalog.md)(상품·재고)가 먼저 준비되어야 시작할 수 있다.

## Cart

### 엔티티

id, user, cartItems[ { product, size, color, quantity } ]

### API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/cart` | 내 장바구니 조회 | 회원 |
| POST | `/api/cart/items` | 상품 담기 | 회원 |
| PUT | `/api/cart/items/{itemId}` | 수량 변경 | 회원 |
| DELETE | `/api/cart/items/{itemId}` | 항목 삭제 | 회원 |
| DELETE | `/api/cart` | 전체 비우기 | 회원 |

### 규칙

- 회원 1명당 장바구니 1개 (1:1)
- **"동일 항목"은 상품ID + 옵션 조합으로 판단한다.** 같은 상품이라도 사이즈·색상이 다르면 별개 항목이다
- 동일 항목 재담기 시 수량 합산
- 재고 초과 수량은 담을 수 없다 → `CT002 CART_QUANTITY_EXCEEDED`

## Order

### 엔티티

id, user, orderItems[ { product, size, color, quantity, priceAtOrder } ], totalAmount,
status(PENDING/PAID/SHIPPING/DELIVERED/CANCELLED), shippingAddress, createdAt

### API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/orders` | 주문 생성 | 회원 |
| GET | `/api/orders` | 내 주문 목록 | 회원 |
| GET | `/api/orders/{id}` | 주문 상세 | 회원 |
| POST | `/api/orders/{id}/cancel` | 주문 취소 | 회원 |
| GET | `/api/admin/orders` | 전체 주문 목록 | ADMIN |
| PUT | `/api/admin/orders/{id}/status` | 주문 상태 변경 | ADMIN |

### 규칙

- 주문 생성 시 재고 차감. **동시에 마지막 재고를 두고 경쟁해도 음수로 내려가면 안 된다**
- 주문 시점 가격을 `priceAtOrder`에 **스냅샷** 저장 — 이후 상품 가격이 바뀌어도 이미 성립한 주문의
  금액은 유지되어야 한다
- PAID 이후 취소 시 재고 복구
- SHIPPING 이후 회원이 직접 취소 불가 → `O002 ORDER_CANCEL_NOT_ALLOWED`

### 착수 전 정할 것

- **동시성 제어 방식** — 비관적 락(`PESSIMISTIC_WRITE`), 낙관적 락+재시도, 원자적 감소 연산 중 선택.
  트레이드오프를 비교하고 **ADR로 남긴다**([decisions/](../decisions/))
- 배송지도 주문 시점 스냅샷이 필요한지
- 상태 전환표(각 상태에서 허용되는 다음 상태)를 확정

## Payment

### 엔티티

id, order, amount, method(CARD/TRANSFER), status(SUCCESS/FAILED/REFUNDED), paidAt

### API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/payments` | 결제 요청 | 회원 |
| GET | `/api/payments/{orderId}` | 결제 정보 조회 | 회원 |
| POST | `/api/payments/{id}/refund` | 환불 요청 | 회원 |

### 규칙

- **1 Order : 1 Payment** — 초기 범위는 부분·분할 결제를 열어두지 않는다
- 결제 성공 → Order status `PAID` 전환
- 결제 실패 → 재고 복구
- 중복 결제 시도 → `PAY002 PAYMENT_ALREADY_EXISTS`
- 외부 PG 연동은 `PaymentGateway` 인터페이스로 추상화하고 **우선 Mock으로 구현**한다.
  실제 PG(포트원 등) 연동 여부는 미결이다 — [roadmap.md](../roadmap.md)

### 착수 전 정할 것

- 중복 결제 방어를 서비스 계층에서 할지 DB 유니크 제약으로 할지 (둘 다 두는 것도 선택지)
- 환불 시 결제 상태·주문 상태·재고를 갱신하는 **순서**를 명확히 정의

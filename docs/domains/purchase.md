# cart / order / payment — 구매 플로우

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../roadmap.md)가 소유한다.
> `CartErrorCode`·`OrderErrorCode`·`PaymentErrorCode`만 선반영돼 있다.

장바구니 → 주문 → 결제로 이어지는 핵심 흐름. [auth.md](auth.md)(로그인 사용자)와
[catalog.md](catalog.md)(상품·재고)가 먼저 준비되어야 시작할 수 있다.

## 엔티티 관계

```
User  1 ──  1  Cart          cart.user_id (unique)
Cart  1 ──  N  CartItem      cart_item.cart_id, orphanRemoval = true
User  1 ──  N  Order         orders.user_id
Order 1 ──  N  OrderItem     order_item.order_id
Order 1 ──  1  Payment       payment.order_id (unique)

CartItem  N ── 1  Product    단방향. Product는 장바구니를 모른다
OrderItem N ── 1  Product    단방향
```

### Cart와 Order 사이에는 연관관계를 두지 않는다

장바구니 하나에서 주문이 여러 번 나오므로 **논리적 cardinality는 `Cart 1 : Order N`이 맞다.**
그러나 그 사실은 `User 1 : Order N`이 이미 표현하고(Cart는 User와 1:1), FK로 매핑하지 않는다.

- **가리킬 행이 사라진다.** 주문이 성립하면 그 `cartItem`을 지우고, `DELETE /api/cart`로 전체를
  비울 수도 있다. `OrderItem`이 `cart_item_id`를 들면 dangling FK가 된다
- **스냅샷이 깨진다.** Order는 `priceAtOrder`로 주문 시점을 고정한 기록이고 Cart는 지금 바뀌는
  상태다. 고정된 기록이 살아있는 행을 참조하면 고정의 의미가 없다
- **양방향이 된다.** `Cart → Order` FK는 장바구니가 주문을 알게 만들어
  [architecture.md](../architecture.md)의 단방향 규칙에 걸린다

`cartItemId` 목록은 **요청 입력일 뿐 영속 관계가 아니다.** `OrderService`가 그 id로 `CartItem`을
읽어 `OrderItem`으로 값을 복사하고 원본을 지운다. 복사가 끝나면 연결은 끊긴다.

### 도메인 간 의존은 `order → cart` 단방향이다

cart는 order를 모른다. **`OrderService`는 `CartService`를 경유한다** —
[ADR 0014](../decisions/catalog/0014-category-reads-product-repository.md)가 Repository로 내린 것은
순환을 끊기 위해서였고 `order → cart`에는 순환이 없다. 순환이 없을 때의 선례는 `auth → user`이며
그쪽은 Service를 경유한다. 또 주문은 장바구니에서 **읽고 지우는** 둘을 하므로, 빈 장바구니 처리 같은
cart 쪽 규칙이 `CartService`에 모여 있어야 `OrderService`로 새지 않는다.

## Cart

### 엔티티

id, user, cartItems[ { product, quantity } ]

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
- **"동일 항목"은 상품ID로 판단한다.** 옵션(사이즈·색상)은 초기 범위 밖이다 —
  `Product`에 옵션이 없고 재고도 상품 단위 하나뿐이어서, 옵션을 받으면 검증할 원천이 없고
  옵션별 재고도 표현할 수 없다. 도입 조건은 [roadmap.md](../roadmap.md)의 미결 사항에 있다
- 동일 항목 재담기 시 수량 합산
- 재고 초과 수량은 담을 수 없다 → `CT002 CART_QUANTITY_EXCEEDED`.
  **검사 대상은 합산 후 수량(기존 + 요청)이다** — 1개씩 나눠 담아 재고를 넘기는 경로를 막는다

### 담기 검사는 재고를 점유하지 않는다

담기 시점에 재고를 검사하지만 **차감하지도 예약하지도 않는다.** 담은 뒤 품절될 수 있고, 그때는
주문 생성이 `P002`로 거절한다. **재고 점유는 결제 대기(주문 `PENDING`)에서 일어난다** — [Order](#order) 참고.

그러면서도 담기에서 검사하는 이유는 [ADR 0015](../decisions/catalog/0015-no-stock-quantity-in-public-response.md)가 공개 응답에서 재고 수량을 뺐기 때문이다.
FE가 수량 상한을 알 수 없으므로, 담기에서 걸러주지 않으면 사용자는 담아둔 뒤 **주문 단계에서야**
거절당한다. 그 거절은 사용자가 감수할 부분이 아니다.

- 담기 검사(`CT002`)는 **UX용 선행 거절**이고 보장이 아니다
- 주문 생성의 `P002`(`Product.decreaseStock()`)가 **유일한 방어선**이다.
  담기에서 걸렀다는 이유로 주문에서 생략하지 않는다
- ⚠️ [ADR 0015](../decisions/catalog/0015-no-stock-quantity-in-public-response.md) 본문은 담기 거절을 `P002`로 적었다. 담기는 `decreaseStock()`을 호출하지 않으므로
  그 경로의 코드는 `CT002`다. ADR은 고치지 않으므로 이 문서가 바로잡는다

### 조회 응답

- 항목별 **구매 가능 여부를 불리언으로** 담는다 — 담은 뒤 재고가 줄었을 수 있다.
  재고 수량은 담지 않는다([ADR 0015](../decisions/catalog/0015-no-stock-quantity-in-public-response.md)). 그 ADR이 "상한이 필요하면 장바구니 응답에 둔다"고 열어둔 자리지만,
  수량을 담으면 회원가입만으로 재고를 읽을 수 있어 불리언으로 시작한다 — 필요해지면 더한다
- **서버가 수량을 자동 조정하지 않는다.** 사용자가 넣은 값은 그대로 두고 주문 시점에 거절한다

## Order

### 엔티티

id, user, orderItems[ { product, quantity, priceAtOrder } ], totalAmount,
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

- **입력은 선택한 `cartItemId` 목록이다.** 장바구니 전체가 아니라 사용자가 고른 항목만 주문한다
  - 주문이 성립하면 **그 항목만** 장바구니에서 지운다. 선택하지 않은 항목은 남는다
  - 타인의 항목이거나 존재하지 않는 id → `CT001 CART_ITEM_NOT_FOUND`
  - 빈 목록 → `C001 INVALID_INPUT_VALUE`
  - **장바구니를 거치지 않는 "바로 구매"는 초기 범위 밖이다** — 입력이 장바구니에 묶인다
- 주문 생성 시 재고 차감. **동시에 마지막 재고를 두고 경쟁해도 음수로 내려가면 안 된다**
- **재고 점유는 결제 대기에서 일어난다.** 주문 생성(`PENDING`)의 차감이 곧 점유다.
  결제 성공이 점유를 확정(`PAID`)하고, 결제 실패·취소가 복구한다. 장바구니는 점유하지 않는다
- **`cartItem` 삭제와 재고 차감은 한 트랜잭션이다.** 재고 부족(`P002`)으로 주문이 실패하면
  장바구니는 그대로 남아야 한다 — 거절당했는데 장바구니까지 비는 것이 최악이다
- 주문 시점 가격을 `priceAtOrder`에 **스냅샷** 저장 — 이후 상품 가격이 바뀌어도 이미 성립한 주문의
  금액은 유지되어야 한다
- **`CANCELLED`로 가는 모든 전환이 재고를 복구한다.** 점유를 시작한 것이 주문 생성이므로
  결제 전(`PENDING`)이든 후(`PAID`)든, 회원이 취소하든 ADMIN이 바꾸든 같다.
  결제 실패([Payment](#payment))와 환불도 이 규칙에 흡수된다
- SHIPPING 이후 회원이 직접 취소 불가 → `O002 ORDER_CANCEL_NOT_ALLOWED`

### 착수 전 정할 것

- **동시성 제어 방식** — 비관적 락(`PESSIMISTIC_WRITE`), 낙관적 락+재시도, 원자적 감소 연산 중 선택.
  트레이드오프를 비교하고 **ADR로 남긴다**([decisions/](../decisions/))
- **`PENDING` 주문의 만료 처리** — 결제되지 않은 주문이 재고를 계속 점유한다. 장바구니 예약을
  배제한 이유(담아두고 사지 않으면 재고가 묶인다)가 범위만 좁혀 그대로 재현된다.
  자동 취소 배치를 둘지, 사용자·ADMIN 취소에만 맡길지 정한다
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

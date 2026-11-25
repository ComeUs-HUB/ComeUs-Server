# 구매 플로우 — cart / order / payment

> 이 문서는 **계약**이고, 무엇이 끝났는지는 [roadmap.md](../../roadmap.md)가 소유한다.
> **Cart·Order·Payment 모두 코드에 있다.** 주문부터 승인까지 한 장 그림은 [seq-purchase-overview.md](../../diagrams/purchase/seq-purchase-overview.md).

장바구니 → 주문 → 결제로 이어지는 핵심 흐름. [auth.md](../auth.md)(로그인 사용자)와
[catalog/](../catalog/README.md)(상품·재고)가 먼저 준비되어야 시작할 수 있다.

도메인별 계약은 세 문서가 나눠 소유한다. 이 문서는 **셋에 걸친 것만** 담는다.

| 도메인 | 문서 | 무엇을 소유하나 |
|---|---|---|
| cart | [cart.md](cart.md) | 담기·수량 변경, 담기 시점 재고 검사 |
| order | [order.md](order.md) | 주문 생성·취소, 재고 점유와 복구, 상태 전환 |
| payment | [payment.md](payment.md) | 결제·환불, PG 추상화 |

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

**Cart와 Order 사이에는 연관관계를 두지 않는다.** 주문은 장바구니를 값으로 복사하고 원본을 지운다 —
`cartItemId` 목록은 요청 입력일 뿐 영속 관계가 아니다. 근거는 [ADR 0017](../../decisions/purchase/0017-order-copies-cart-and-cuts-the-link.md).

도메인 간 의존은 셋이다. cart는 order·payment를 모른다.
방향과 경유 지점은 [architecture.md의 의존 규칙](../../architecture.md#의존-규칙)이 소유한다.

- `order → cart` — 주문 생성이 `CartService`를 경유해 항목을 읽고 지운다
- `payment → order` — 결제의 상태 CAS는 `OrderRepository`를 직접 쓰고, 재고 복구는 `OrderService`를 경유한다([ADR 0031](../../decisions/purchase/0031-payment-uses-order-repository-for-state-cas.md))
- `order → payment` — `PAID` 주문 취소가 `OrderCancelService → RefundService`로 환불을 부른다

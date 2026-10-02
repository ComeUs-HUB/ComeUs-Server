# 구매 플로우 — cart / order / payment

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `CartErrorCode`·`OrderErrorCode`·`PaymentErrorCode`만 선반영돼 있다.

장바구니 → 주문 → 결제로 이어지는 핵심 흐름. [auth.md](../auth.md)(로그인 사용자)와
[catalog.md](../catalog.md)(상품·재고)가 먼저 준비되어야 시작할 수 있다.

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

도메인 간 의존은 `order → cart` 단방향이고 cart는 order를 모른다. 방향과 경유 지점
(`OrderService` → `CartService`)은 [architecture.md의 의존 규칙](../../architecture.md#의존-규칙)이 소유한다.

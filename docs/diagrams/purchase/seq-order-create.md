# 주문 생성

- **답하는 질문**: 동시 주문에서 재고가 초과 판매되지 않고, 같은 요청이 왜 주문을 두 번 만들지 않는가
- **범위**: `POST /api/orders` 한 건
- **원천**: [order.md](../../domains/purchase/order.md) · ADR [0024](../../decisions/purchase/0024-order-idempotency-key-in-header.md) · [0025](../../decisions/purchase/0025-pessimistic-lock-as-stock-concurrency-baseline.md) · [0027](../../decisions/purchase/0027-idempotency-uk-violation-responds-conflict.md)

![주문 생성 시퀀스](../images/seq-order-create.png)

<details>
<summary>Mermaid 원본 — 내용은 이 블록이 기준이고 PNG는 여기서 다시 그린다</summary>

```mermaid
sequenceDiagram
    actor U as 회원
    participant C as OrderController
    participant S as OrderService
    participant D as Cart · Product
    participant M as MySQL

    U->>C: POST /api/orders + Idempotency-Key
    Note over U,C: 키 형식 · 빈 목록 · 배송지 검증. 위반이면 400 C001
    C->>S: createOrder(...)
    Note over S,M: READ COMMITTED 트랜잭션 하나로 묶는다
    S->>D: lockForOrder(userId)
    D->>M: carts FOR UPDATE
    Note over S,M: 같은 회원의 요청이 여기서 줄을 선다. 장바구니가 없으면 404 CT001
    S->>M: 멱등키 조회
    opt 같은 멱등키의 주문 있음
        S-->>U: 200 기존 주문 (재고 변경 없음)
    end
    S->>D: selectItems(ids) — 없으면 CT001
    S->>D: lockVisibleProducts(ids)
    D->>M: products FOR UPDATE (id 순)
    Note over S,M: 잠근 뒤에 상품 상태를 읽는다. 숨김 상품이면 404 P001
    S->>D: decreaseStock(q) — 부족하면 P002
    S->>M: orders · order_items INSERT
    S->>D: removeItems(ids)
    C-->>U: 200 { orderNumber, amount }
```

</details>

## 읽는 법

- **잠금은 `carts` → `products` 순이다.** `carts` 잠금이 같은 회원의 요청을 줄 세우고, `products` 잠금이 다른 회원과의 재고 경합을 막는다
- **멱등키 조회가 `carts` 잠금 뒤에 있다.** 그래야 먼저 커밋된 주문이 보인다. 앞단을 우회한 중복은 uk가 막고 `C005`(409)로 끝난다
- **상품은 잠그기 전에 읽지 않는다.** Hibernate는 이미 읽은 엔티티를 잠금 쿼리로 갱신하지 않아, 먼저 읽으면 낡은 재고로 차감한다
- 재고 부족으로 실패하면 트랜잭션 전체가 롤백되어 장바구니도 그대로 남는다

# 장바구니 담기

- **답하는 질문**: 같은 회원의 동시 담기에서 수량이 사라지지 않고, 첫 담기 경합이 왜 409가 되는가
- **범위**: `POST /api/cart/items` 한 건. 주문이 장바구니를 쓰는 부분은 계획이라 점선으로만 표시한다
- **원천**: [cart.md](../../domains/purchase/cart.md) · ADR [0016](../../decisions/purchase/0016-stock-reserved-at-order-not-cart.md) · [0019](../../decisions/purchase/0019-cart-owns-item-deletion.md) · [0020](../../decisions/purchase/0020-cart-writes-use-read-committed.md)

![장바구니 담기 시퀀스](../images/seq-cart-add.png)

<details>
<summary>Mermaid 원본 — 내용은 이 블록이 기준이고 PNG는 여기서 다시 그린다</summary>

```mermaid
sequenceDiagram
    autonumber
    actor U as 회원
    participant CC as CartController
    participant CS as CartService
    participant PS as ProductService
    participant UsS as UserService
    participant CR as CartRepository
    participant Cart as Cart (엔티티)
    participant DB as MySQL

    U->>CC: POST /api/cart/items { productId, quantity }
    Note over CC: @Valid — quantity 1~99 밖이면 400 C001
    CC->>CS: addItem(userId, productId, quantity)

    rect rgba(80,140,220,0.15)
        Note over CS,DB: 트랜잭션 — READ COMMITTED<br/>(기본 REPEATABLE READ면 잠근 뒤에도 옛 스냅샷을 읽어 갱신이 사라진다)
        CS->>PS: getVisibleProduct(productId)
        Note over CS,PS: 상품을 먼저 읽는다 — P001일 때 빈 장바구니 행이 남지 않게
        alt 없거나 HIDDEN
            PS-->>U: 404 P001
        end

        CS->>CR: existsByUserId(userId)
        alt 장바구니 있음
            CR->>DB: select ... for update (carts 행 1개 레코드 락)
            CS->>CR: findByUserId — 항목·상품을 쿼리 1회로
            CS->>Cart: addItem(product, quantity)
            Note over Cart: 새 종류면 50종 상한 → 409 CT003<br/>합산은 long으로 계산 → 재고 초과면 409 CT002
            CS->>CR: flush
        else 장바구니 없음 (첫 담기)
            CS->>UsS: getUser(userId)
            CS->>Cart: Cart.create(user) · addItem
            CS->>CR: saveAndFlush
            alt 동시 첫 담기로 uk_carts_user_id 위반
                CR-->>U: 409 C005 (교착이 아니라 제약 위반으로 끝난다)
            end
        end
    end
    CC-->>U: 200 CartResponse (항목별 purchasable)

    rect rgba(128,128,128,0.08)
        Note over U,DB: 계획 — Order: 같은 장바구니를 잠그고 선택 항목을 읽어 재고 차감 + 항목 삭제를 한 트랜잭션으로.<br/>락 순서·멱등성 키·격리 수준 가드는 동시성 ADR에서 정한다
    end
```

</details>

## 읽는 법

- **장바구니는 재고를 점유하지 않는다.** 담을 때 재고를 넘는지만 보고, 차감은 주문이 한다(ADR 0016).
  그래서 담은 뒤 재고가 줄면 항목은 남고 `purchasable`만 `false`가 된다
- 없는 행에 `FOR UPDATE`를 걸면 MySQL이 gap lock을 잡아 교착이 날 수 있다. 그래서 존재를 먼저 확인하고,
  없으면 잠그지 않고 만든 뒤 **uk 제약이 경합을 막게** 한다
- 격리 수준은 `CartService`가 트랜잭션을 **새로 열 때만** 적용된다. 바깥 트랜잭션 안에서 부르면 Spring이 조용히 무시한다 —
  Order가 이 메서드를 쓸 때의 전제다(ADR 0020)
- 수량 변경·항목 삭제·비우기도 같은 락 경로(`findLockedCart`)를 탄다. 장바구니가 없을 때 수량 변경·항목 삭제는 CT001, 비우기는 빈 장바구니 응답이다

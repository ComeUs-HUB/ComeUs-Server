# ADR 0025 — 재고 동시성은 비관적 락을 기준선으로 시작하고 전환은 측정치로 판단한다

- 상태: 채택
- 일자: 2026-11-03
- 관련: [ADR 0016](0016-stock-reserved-at-order-not-cart.md), [ADR 0020](0020-cart-writes-use-read-committed.md), [ADR 0024](0024-order-idempotency-key-in-header.md), [performance/order-stock-concurrency.md](../../performance/order-stock-concurrency.md)

## 맥락

### 0016이 열어둔 칸

[ADR 0016](0016-stock-reserved-at-order-not-cart.md)은 재고 점유를 주문 생성으로 고정하고
`Product.decreaseStock()`의 `P002`를 **유일한 방어선**으로 못 박았지만, 동시성 제어 방식은
"이 ADR이 정하지 않는다"며 비관적 락·낙관적 락+재시도·원자적 감소 셋을 열어뒀다. Order 착수에는
그 칸이 채워져야 한다.

보호 대상은 읽고-검사하고-쓰는 구간이다.

```java
public void decreaseStock(int quantity) {
    requirePositive(quantity);
    if (this.stockQuantity < quantity) throw new BusinessException(OUT_OF_STOCK);  // P002
    this.stockQuantity -= quantity;
    if (!isHidden()) applyStockStatus();   // 0이면 SOLD_OUT
}
```

제어가 없으면 두 트랜잭션이 같은 `stockQuantity`를 읽어 둘 다 검사를 통과하고 둘 다 차감한다 → 음수 재고.

### 선택의 근거로 쓸 숫자가 없다

목표는 대용량·고가용성이지만 문서에 목표 TPS도, 상품당 피크 부하도 없다. 그리고 어느 방식이 맞는지는
**전체 TPS가 아니라 한 상품 행에 몰리는 집중도**가 결정한다 — 전체 1만 TPS가 상품 수천 개에 흩어지면
비관적 락으로 충분하고, 한 상품에 2천이 몰리면 원자적 감소로도 부족하다.

여기서 추정으로 고르면 **ADR의 근거 칸에 추정치가 들어간다.** 되돌리는 비용이 가장 큰 쪽(재고 권위를
DB 밖으로 옮기는 것)을 근거 없이 고르는 경로도 열린다. 그래서 이 ADR은 방식 자체가 아니라
**무엇을 재고 무엇을 보면 전환하는가**를 결정한다.

## 결정

**비관적 락을 기준선으로 구현하고, 전환은 [측정 계획](../../performance/order-stock-concurrency.md)의
측정치로만 판단한다.**

`products` 행을 `SELECT … FOR UPDATE`로 잠근 뒤 `decreaseStock()`을 호출한다. 고른 근거는 처리량이 아니다.

- **구현 비용이 가장 낮다** — `@Lock(PESSIMISTIC_WRITE)` 한 줄. 가장 빨리 측정 가능한 상태에 도달한다
- **도메인 규칙이 엔티티에 남는다** — `P002` 판정과 `SOLD_OUT` 전환이 `Product` 안에 있다
- **정합성이 가장 확실해 다른 셋을 비교할 기준점이 된다** — 처리량이 더 높은 방식은 모두 정합성을 일부 내놓는다
- **되돌리는 비용이 낮다** — 락을 떼고 차감 문장을 바꾸는 범위다. 기준선이 틀렸을 때의 손실이 작다

함께 고정하는 것 셋이다. 빠뜨리면 기준선 자체가 틀린 측정을 만든다.

| 항목 | 결정 |
|---|---|
| **락 순서** | `carts` 루트 락 → `products` 락. `products`는 **`id` 오름차순으로 정렬해 잠근다** — 두 주문이 같은 두 상품을 반대 순서로 잠그면 교착이다 |
| **격리 수준** | 주문 트랜잭션도 `READ_COMMITTED`. [ADR 0020](0020-cart-writes-use-read-committed.md)의 `CartService` 쓰기를 기본값(REPEATABLE READ) 트랜잭션 안에서 부르면 Spring이 격리 수준을 **조용히 무시**해 Lost Update가 되살아난다 |
| **무시 방지 장치** | `JpaTransactionManager.setValidateExistingTransaction(true)`로 격리 수준이 다른 트랜잭션에 참여하면 예외를 띄운다. `REQUIRES_NEW`는 쓸 수 없다 — 재고 차감과 `cartItem` 삭제가 한 트랜잭션이어야 한다([order.md](../../domains/purchase/order.md)) |

### 1차 실패 모드는 처리량이 아니라 커넥션 풀이다

```
tx begin (커넥션 획득) → SELECT … FOR UPDATE ─ 여기서 블록, 커넥션을 계속 점유
```

대기자가 **DB 커넥션을 쥐고 기다린다.** 현재 HikariCP 설정이 없어 풀은 기본값 10이고, 인기 상품 하나에
동시 대기자가 그만큼 쌓이면 풀이 비어 **상품 조회·로그인 같은 무관한 요청까지 커넥션을 받지 못한다.**
한 상품의 경합이 서비스 전체를 끌어내린다.

대기가 자라는 모양은 대기행렬로 설명된다.

```
ρ = λ × S          λ = 상품당 도착률    S = 락 보유 시간
평균 대기 ≈ S × ρ/(1-ρ)                ρ → 1 이면 발산

락 보유 구간 = products FOR UPDATE 획득 → 커밋
             = decreaseStock + orders INSERT + order_items INSERT(N) + cart_items DELETE + 커밋
```

ρ = 0.8에서 이미 평균 대기가 락 보유 시간의 4배다. `S`의 실제 값이 상품당 상한을 직접 결정하므로
**측정의 첫 지표는 처리량이 아니라 커넥션 풀 포화 지점**이다.

## 측정 대기 — 배제하지 않은 셋

아래는 **배제가 아니다.** 측정 후 전환 후보이고, 각각이 내놓는 대가를 미리 적어둔다.

| 방식 | 예상 이점 | 대가 |
|---|---|---|
| **원자적 감소** `UPDATE … SET stock = stock - ? WHERE id = ? AND stock >= ?` | 락 보유가 왕복 1회로 줄어 상한이 오른다 | **도메인 규칙이 SQL로 새어나간다** — `P002` 판정(affected rows 0)과 `SOLD_OUT` 전환이 `Product` 밖으로 나간다. JPA 1차 캐시가 낡으므로 `clearAutomatically` 처리도 따라온다 |
| **분산 락** (Redisson `RLock`) | 대기를 트랜잭션 **밖**으로 밀어내 커넥션 풀을 보호한다. 상품당 상한 자체는 비슷하다 | 해제가 **커밋 이후**여야 한다(아니면 다음 요청이 낡은 재고를 읽는다). lease TTL이 최대 트랜잭션 시간보다 길어야 한다(짧으면 상호배제가 깨진다). Redis가 주문 성공 경로의 장애점이 된다 |
| **Redis 재고 게이트** (`DECRBY`) | 못 살 요청을 DB 전에 거절해 상한이 크게 오른다 | **재고의 권위가 DB 밖으로 나간다** — [ADR 0016](0016-stock-reserved-at-order-not-cart.md)의 "`P002`가 유일한 방어선" 전제를 대체한다. 정합 배치와 과판매 복구가 따라온다 |

분산 락과 Redis 게이트를 구분해 적는 이유는 둘이 자주 섞이기 때문이다 — **분산 락은 처리량을 올리는
도구가 아니라 DB 락의 부작용(커넥션 점유) 완화**이고, 상한을 실제로 올리는 것은 원자적 감소와 Redis 게이트다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 낙관적 락 + 재시도 (`@Version`) | 경합이 심한 인기 상품에서 재시도가 비선형으로 늘고, **재시도하는 동안에도 커넥션을 점유**한다 — 비관적 락의 1차 실패 모드를 공유하면서 정합성 코드만 복잡해진다. 스키마에 버전 컬럼도 필요하다. [ADR 0020](0020-cart-writes-use-read-committed.md)이 장바구니에서 같은 이유로 배제했다 |
| 측정 전에 원자적 감소부터 고른다 | 상한이 더 높은 것은 맞지만, 고르는 근거가 "대용량이 목표니까"라는 추정이 된다. 도메인 규칙 누출이라는 실제 대가를 숫자 없이 지불하는 셈이다 |
| 재고 분할(세그먼트 카운터)·선착순 큐 | 상품당 1,000 TPS를 넘는 구간의 도구다. 그 구간에 있다는 측정치가 먼저 필요하고, Redis 게이트보다 뒤에 온다 |
| 테이블 락·애플리케이션 전역 락 | 보호 대상이 상품 행 하나인데 범위가 전역으로 커진다. 서로 다른 상품의 주문이 서로를 기다린다 |
| 분산 락을 "다중 서버니까" 기준선으로 | 보호 대상이 DB 행 하나이고 DB가 전역 상호배제를 제공한다. 인스턴스가 늘어도 `FOR UPDATE`는 프라이머리에서 직렬화되므로, 인스턴스 수는 분산 락의 근거가 아니다. 근거는 **커넥션 풀 보호**이고 그건 측정 대상이다 |

## 결과 / 되돌리려면

- 미결 항목이 해소되므로 두 ADR의 상태 줄을 `채택 (0025가 보강)`으로 바꾼다. 본문은 그대로 둔다 —
  [0016](0016-stock-reserved-at-order-not-cart.md)(동시성 제어 방식),
  [0020](0020-cart-writes-use-read-committed.md)(주문 트랜잭션의 격리 수준)
- **전환을 촉발하는 측정값은 셋이다** — 상품당 TPS 상한, 커넥션 풀 포화 지점, p99 지연.
  정의와 시나리오는 [측정 계획](../../performance/order-stock-concurrency.md)이 소유한다.
  전환하면 새 ADR이 이 ADR을 `대체됨`으로 바꾼다
- **정합성 수용 기준이 깨지는 것은 전환 사유가 아니라 버그다.** 음수 재고·과판매는 어느 방식에서도 0이어야 한다
- **[ADR 0024](0024-order-idempotency-key-in-header.md)의 멱등키 대조가 `carts` 루트 락에 의존한다.**
  전환으로 그 락이 사라지면 "두 번째 요청이 첫 주문을 확실히 본다"가 무너진다 — 그때 대조를 다시 설계해야 한다.
  DB 유니크 제약은 전략과 무관하게 유효하므로 재고 이중 차감은 계속 막힌다
- 측정은 **MySQL 실기동이어야 한다.** H2는 격리 수준과 락이 달라 `./gradlew test`로는 검증되지 않는다
- `PENDING` 주문의 만료 처리는 이 ADR의 범위 밖이다 — 결제되지 않은 주문이 재고를 계속 점유하는 문제는
  동시성 제어 방식과 무관하게 남는다([order.md](../../domains/purchase/order.md))
- 되돌리려면 락을 떼고 차감 문장을 바꾸면 되지만, **측정치 없이 전환하지 않는다.** 그것이 이 ADR의 결정이다

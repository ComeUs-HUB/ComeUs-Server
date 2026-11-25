# ADR 0031 — 결제는 주문 상태 CAS를 `OrderRepository`로 직접 실행한다

- 상태: 채택
- 일자: 2026-11-25
- 관련: [architecture.md](../../architecture.md), [domains/purchase/payment.md](../../domains/purchase/payment.md),
  [ADR 0026](0026-payment-confirm-outside-transaction.md), [ADR 0029](0029-refund-outside-transaction.md),
  [ADR 0014](../catalog/0014-category-reads-product-repository.md)

## 맥락

[architecture.md](../../architecture.md)의 의존 규칙은 "도메인 간 참조는 리포지토리가 아니라 서비스를
경유한다"이고, 예외는 [ADR 0014](../catalog/0014-category-reads-product-repository.md)의
`category → product` 하나였다.

Payment를 구현하면서 **결제가 주문 상태를 바꾸는 쿼리**가 생겼다. 승인과 환불은 선점·호출·반영으로
나뉘고([ADR 0026](0026-payment-confirm-outside-transaction.md)·[0029](0029-refund-outside-transaction.md)),
선점과 반영은 모두 `orders`의 조건부 UPDATE(CAS)다.

- 선점 — `claimPayment`(`PENDING → PAYMENT_IN_PROGRESS` + `payment_key`), `transitionStatus`(`PAID → REFUND_IN_PROGRESS`)
- 반영 — `applyPaymentSuccess`·`applyPaymentRejection`·`applyRefundResult`. 복구 배치는 읽은 `updated_at`까지 비교한다
- 복구 대상·경보 조회 — `findRecoveryTargetIds`·`findAbandonedOrderNumbers`

반영 TX는 **CAS와 `payments` 저장이 같은 트랜잭션**이어야 하고, 트랜잭션 경계(격리 수준·타임아웃)는
`PaymentTransitions`·`RefundTransitions`가 정한다.

## 결정

**`payment` 패키지의 `PaymentTransitions`·`RefundTransitions`·`PaymentRecoveryService`·`PaymentQueryService`는
`OrderRepository`를 직접 주입한다.** 상태 CAS와 결제용 조회는 Repository 메서드로 실행한다.

**불변식이 서비스에 있는 일은 `OrderService`를 경유한다.** 판단 기준은 ADR 0014와 같다 —
Repository를 직접 쓰면 우회되는 규칙이 있는가.

| 경유 | 이유 |
|---|---|
| CAS·조회 → `OrderRepository` 직접 | 지킬 규칙이 **쿼리의 WHERE 조건 자체**다(`status = :expected`, `updated_at = :read`). 서비스로 감싸도 더 지킬 것이 없다 |
| 재고 복구 → `OrderService.restoreStock` | 상품을 id 오름차순으로 잠그는 규칙([ADR 0025](0025-pessimistic-lock-as-stock-concurrency-baseline.md))이 서비스에 있다. `propagation = MANDATORY`로 반영 TX에 참여한다 |
| 선점 실패 응답 → `OrderService.cancelRejection` | 상태별 거절 코드(O002·O003·O004)를 주문 취소와 같은 표로 고른다 |

반대 방향 `order → payment`는 `OrderCancelService → RefundService` 하나다. `PAID` 주문 취소가 환불을
불러야 해서 생겼다. `OrderService`가 아니라 트랜잭션 없는 `OrderCancelService`가 부르므로
생성자 주입 순환이 없다(`OrderCancelService → RefundService → OrderService`).

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `OrderService`에 CAS 위임 메서드를 둔다 | 결제 상태 전이 6~7개가 그대로 통과하는 메서드가 된다. 지키는 규칙은 없고, 각 메서드에 `MANDATORY`를 걸어 결제 TX에 참여시켜야 해서 트랜잭션 경계가 두 클래스에 흩어진다 |
| 주문 상태 전이를 모두 `order`가 소유하고 결제는 이벤트로 알린다 | 선점은 **동기 응답**이다. CAS 결과(1건/0건)로 토스 호출 여부와 응답 코드를 그 자리에서 정해야 한다 |
| 결제 상태를 `payments` 테이블로 옮겨 `orders`를 건드리지 않는다 | 승인 전에는 `payments` 행이 없다(성공한 결제만 저장). 주문 상태 하나로 "결제 중·환불 중"을 막아야 배송 처리·회원 취소와의 경합을 같은 CAS로 판정할 수 있다 |

## 결과 / 되돌리려면

- `architecture.md`의 의존 규칙 목록에 `payment → order`(Repository 직접)와 `order → payment`를 추가한다
- 결제가 쓰는 `OrderRepository` 메서드는 **결제 상태 전이 전용**이다. 주문 도메인의 다른 쓰기(생성·만료·배송)는
  계속 `OrderService`가 소유한다
- 이 예외는 `payment`–`order` 한 쌍에 한정한다. "Repository 직접 주입"을 일반 규칙으로 올리지 않는다
- 되돌리려면: 결제 CAS를 `OrderService`의 `MANDATORY` 메서드로 옮긴다. 쿼리는 그대로이고
  호출 경로만 바뀌므로 동작 변화 없이 옮길 수 있다

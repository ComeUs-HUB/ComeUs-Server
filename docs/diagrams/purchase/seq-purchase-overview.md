# 주문 · 결제 전체 흐름

- **답하는 질문**: 주문 생성부터 결제 승인까지, 재고는 언제 확보되고 돈은 언제 확정되는가
- **범위**: `POST /api/orders` → 토스 결제창 → `POST /api/payments/confirm`. 단계별 세부는 아래 그림들이 소유한다
- **원천**: [order.md](../../domains/purchase/order.md) · [payment.md](../../domains/purchase/payment.md) · ADR [0025](../../decisions/purchase/0025-pessimistic-lock-as-stock-concurrency-baseline.md) · [0026](../../decisions/purchase/0026-payment-confirm-outside-transaction.md)

![주문 · 결제 전체 흐름](../images/seq-purchase-overview.png)

## 그림 따라 읽기

| 단계 | 핵심 | 세부 그림 |
|---|---|---|
| 1 주문 생성 | 한 트랜잭션에서 `carts` → `products`(id 순) 잠금 후 **재고 차감**. 돈은 아직 움직이지 않는다 | [seq-order-create.md](seq-order-create.md) |
| 2 결제창 | 토스 인증만 받는다. 승인 전이라 돈은 확정되지 않고, 15분 안에 승인하지 않으면 만료 배치가 취소한다 | — |
| 3 승인 | 선점 CAS로 한 요청만 토스를 부르고, 토스 호출은 트랜잭션 밖이다. 결과를 모르면 상태를 두고 배치에 맡긴다 | [seq-payment-confirm.md](seq-payment-confirm.md) · [seq-payment-recovery.md](seq-payment-recovery.md) |

주황 화살표 두 개가 이 흐름의 축이다. **재고는 주문에서 확보하고, 돈은 선점에 성공한 승인 한 번으로 확정한다.**
상태 전이 전체는 [state-order.md](state-order.md), `PAID` 이후 취소는 [seq-refund.md](seq-refund.md).

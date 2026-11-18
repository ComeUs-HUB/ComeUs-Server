# ADR 0029 — 환불은 승인과 같이 선점·호출·반영으로 나누고 토스 취소가 끝난 뒤에 재고를 푼다

- 상태: 채택 (0030이 보강)
- 일자: 2026-11-07
- 관련: [ADR 0026](0026-payment-confirm-outside-transaction.md), [ADR 0028](0028-payment-result-whitelist-and-recovery-limit.md), [domains/purchase/payment.md](../../domains/purchase/payment.md), [domains/purchase/order.md](../../domains/purchase/order.md)

## 맥락

`PAID` 주문의 취소는 토스 결제 취소를 거쳐야 하는데, 지금은 환불 순서가 미정이라 `O002`로 거절한다.
토스 취소도 승인처럼 **되돌릴 수 없는 외부 호출**이라 [ADR 0026](0026-payment-confirm-outside-transaction.md)과 같은 문제가 그대로 생긴다.

- 하나의 트랜잭션으로 묶으면 토스 취소가 끝난 뒤 커밋이 실패할 때 돈은 돌려줬는데 주문은 `PAID`로 남는다
- 토스가 느린 동안 커넥션과 `orders` 행 락을 쥐어 배송 처리와 다른 요청이 막힌다
- 응답을 잃으면 환불됐는지 알 수 없다

## 결정

**환불을 선점·토스 취소·반영으로 나누고, 토스 취소는 트랜잭션 밖에서 호출한다. 재고는 토스 취소가 끝난 뒤에 푼다.**

```
OrderCancelService.cancel()          트랜잭션 없음. 상태를 보고 분기
├─ PENDING → 기존 결제 전 취소
└─ PAID    → RefundService.refund()  트랜잭션 없음
     ├─ 선점 TX    CAS PAID → REFUND_IN_PROGRESS
     ├─ 토스 취소  POST /v1/payments/{paymentKey}/cancel, Idempotency-Key: cancel-{paymentKey}
     └─ 반영 TX    결과에 따라 전이
```

- 조율 메서드와 트랜잭션 메서드는 다른 빈에 둔다. 자기 호출이면 `@Transactional`이 무시된다
- 진입점이 읽은 상태는 판단용이다. 실제 보호는 각 경로의 CAS가 한다

### 범위

| 항목 | 결정 |
|---|---|
| 환불 가능한 상태 | `PAID`만. `SHIPPING` 이후는 반품 회수가 필요해 범위 밖이다 |
| 환불 금액 | 전액만. `cancelAmount`를 보내지 않는다 |
| 진입점 | 회원은 주문 취소 API 하나로 처리하고 별도 환불 API를 두지 않는다. ADMIN은 상태 변경 API로 같은 경로를 탄다 |

### 진행 중 상태 `REFUND_IN_PROGRESS`

토스 호출 동안의 중복 취소와 배송 처리를 막는다. ADMIN의 `PAID → SHIPPING`도 `PAID`에 대한 CAS라 둘 중 하나만 성공한다.
이 상태의 주문은 회원·ADMIN 모두 취소할 수 없다(`O003`).

### 토스 취소 결과 판정

[ADR 0028](0028-payment-result-whitelist-and-recovery-limit.md)과 같은 원칙이다. **환불되지 않았다고 확인된 코드만 확정 거절**이고 나머지는 결과 불명이다.
코드는 [토스 에러 코드 문서](https://docs.tosspayments.com/reference/error-codes)의 결제 취소 표 33개다.

| 결과 | 판정 | 처리 |
|---|---|---|
| 성공 | 2xx | CAS → `CANCELLED`, `Payment(REFUNDED)`, `products` 잠금 후 재고 복구. 한 트랜잭션 |
| 확정 거절 | 아래 6개 | CAS → `PAID`, ERROR 로그, 응답 `PAY007` |
| 결과 불명 | 나머지 전부, 타임아웃, 응답 유실 | 상태 유지, 응답 `PAY008`, 복구 배치가 판정 |

**확정 거절 (6개)** — 토스가 취소를 받아들이지 않았고 결제는 그대로다

`REFUND_REJECTED` `NOT_CANCELABLE_PAYMENT` `NOT_CANCELABLE_AMOUNT` `EXCEED_MAX_REFUND_DUE` `NOT_CANCELABLE_PAYMENT_FOR_DORMANT_USER` `EXCEED_CANCEL_LIMIT`

사용자가 고칠 수 있는 원인이 아니라 확정 거절에도 ERROR 로그를 남긴다. 고객센터가 결제사 쪽 사유를 확인해야 한다.

**결과 불명 (12개)** — 이미 취소됐거나 일시 오류다

`ALREADY_CANCELED_PAYMENT` `ALREADY_REFUND_PAYMENT` `PROVIDER_ERROR` `FORBIDDEN_CONSECUTIVE_REQUEST` `NOT_AVAILABLE_BANK`
`FAILED_INTERNAL_SYSTEM_PROCESSING` `FAILED_CARD_COMPANY_RESPONSE` `FAILED_REFUND_PROCESS` `FAILED_METHOD_HANDLING_CANCEL` `FAILED_PARTIAL_REFUND` `COMMON_ERROR` `FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING`

"이미 취소됨"을 바로 성공으로 처리하지 않는다. 복구 배치가 조회로 `CANCELED`를 확인하고 같은 반영을 한다 — 판정 경로를 하나로 둔다.

**결과 불명 + 경보 (15개)** — 우리 설정 오류이거나 쓰지 않는 기능(가상계좌, 부분 취소)의 코드다. 나오면 설계 전제가 깨진 것이다

`UNAUTHORIZED_KEY` `INCORRECT_BASIC_AUTH_FORMAT` `FORBIDDEN_REQUEST` `INVALID_REQUEST` `NOT_FOUND_PAYMENT` `INVALID_CANCEL_REQUEST_ID`
`INVALID_REFUND_ACCOUNT_INFO` `INVALID_REFUND_ACCOUNT_NUMBER` `INVALID_BANK` `FORBIDDEN_BANK_REFUND_REQUEST` `FAILED_CLOSED_ACCOUNT_REFUND`
`NOT_ALLOWED_PARTIAL_REFUND_WAITING_DEPOSIT` `NOT_ALLOWED_PARTIAL_REFUND` `EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT` `NOT_MATCHES_REFUNDABLE_AMOUNT`

### 반영 CAS에 `payment_key`를 비교하지 않는다

승인은 거절 뒤 재시도마다 `paymentKey`가 바뀌어 ABA 경로가 있었다. 환불은 결제 하나에 전액 취소 한 번뿐이고 `paymentKey`가 바뀌지 않는다.
상태만으로 충분하다. 선점에 실패한 요청은 토스를 호출하지 않는다.

### 복구

[ADR 0028](0028-payment-result-whitelist-and-recovery-limit.md)의 복구 배치가 `REFUND_IN_PROGRESS`도 함께 판정한다. 판정 시간 5분, 1시간 종결 조건이 같다.

| 토스 조회 결과 | 처리 |
|---|---|
| `CANCELED` | 성공 반영과 같은 전이. 환불 시각은 `cancels[].canceledAt` |
| `DONE` | 취소되지 않았다. `PAID`로 복귀 |
| `PARTIAL_CANCELED`, 그 밖 | 상태 유지, 경보 |
| 조회 실패 | 상태 유지, 다음 주기에 재시도 |

`DONE`으로 되돌린 뒤 재시도하면 같은 멱등키 `cancel-{paymentKey}`가 나간다. 앞선 취소가 실제로 처리 중이었다면 토스가 중복 취소를 막는다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 환불 전체를 한 트랜잭션으로 묶고 실패하면 롤백 | 롤백은 우리 DB만 되돌린다. 토스 취소 뒤 커밋이 실패하면 돈은 돌려줬는데 주문이 `PAID`로 남아 배송될 수 있다 |
| 우리 상태를 먼저 `CANCELLED`로 바꾸고 재고를 푼 뒤 토스 취소 | 토스가 거절하면 결제는 살아 있는데 재고가 이미 팔려 나간다 |
| 별도 환불 API (`POST /api/payments/{orderNumber}/refund`) | 사용자에게는 같은 "취소"다. 진입점이 둘이면 상태 분기가 두 곳에 생긴다 |
| `SHIPPING`·`DELIVERED` 환불 | 반품 회수와 검수가 끼어 별도 상태 기계가 필요하다 |
| "이미 취소됨" 코드를 즉시 성공 처리 | 판정 경로가 둘이 된다. 조회로 확인하는 경로 하나로 충분하다 |

## 결과 / 되돌리려면

- `OrderStatus`에 `REFUND_IN_PROGRESS`가 추가된다. `VARCHAR(20)` 안에 들어가 스키마 변경은 없다
- `PAYMENT_IN_PROGRESS`와 `REFUND_IN_PROGRESS`는 모두 취소를 막으므로 `O003`의 메시지를 "결제 처리 중인 주문"으로 넓힌다
- 에러 코드 `PAY007`(환불 거절), `PAY008`(환불 처리 중)이 추가된다
- 결제 취소 표는 2026-11-07 기준이다. 새 코드는 결과 불명으로 처리되어 안전하다
- 부분 환불이나 배송 후 반품을 열면 이 ADR을 대체하거나 보강한다

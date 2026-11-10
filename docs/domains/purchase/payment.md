# payment — 결제

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `PaymentErrorCode`만 선반영돼 있다.

[order.md](order.md)의 주문 하나에 결제 하나가 붙는다. 셋에 걸친 엔티티 관계는
[README.md](README.md)가 소유한다.

**가상계좌는 초기 범위 밖이다.** 입금까지 일 단위로 걸려 입금 대기 상태와 결제 수단별 `PENDING` 만료가 따로 필요하다.

**PG는 토스페이먼츠다.** 결제창 호출은 FE가 토스 SDK로 하고, 백엔드는 **승인과 결과 반영**을 맡는다.
카드 입력·인증은 토스 결제창 안에서 끝나며 카드 데이터는 백엔드를 지나지 않는다.

## 결제 전체 흐름

```
FE     POST /api/orders                    → { orderNumber, amount }
FE     토스 SDK 결제창 호출                  orderId = orderNumber, amount, successUrl, failUrl
토스   인증 성공 → successUrl (FE 페이지)    ?paymentKey&orderId&amount
FE     POST /api/auth/refresh              새로 로드된 페이지라 액세스 토큰부터 복구
FE     POST /api/payments/confirm          승인과 결과 반영
토스   인증 실패·취소 → failUrl (FE 페이지)  백엔드 호출 없음
```

**successUrl 페이지는 먼저 세션을 복구한다.** 전체 페이지가 새로 로드되어 메모리의 액세스 토큰이 없으므로, [auth.md](../auth.md) FE 계약대로 `POST /api/auth/refresh`를 부른 뒤 승인을 호출한다.

**successUrl·failUrl은 FE 페이지다.** 액세스 토큰이 `Authorization` 헤더로만 실리므로 토스의 리다이렉트는
인증되지 않는다. 백엔드 엔드포인트를 리다이렉트 대상으로 두지 않는다.

**토스 `orderId`는 `Order.orderNumber`다.** PK가 아니다 — 토스 `orderId`는 6자 이상이고, PK를 쓰면 주문 순번이 노출된다.

**failUrl은 주문을 바꾸지 않는다.** 사용자가 결제창을 닫은 경우까지 포함하므로 즉시 취소하면 재시도할 수 없다.
주문은 `PENDING`, 재고는 점유된 채로 남고 같은 주문으로 다시 시도한다.
끝내 결제되지 않은 주문은 [order.md](order.md)의 `PENDING` 만료 처리가 정리한다.

## 엔티티

id, order, paymentKey, amount, method, status(SUCCESS/REFUNDED), paidAt

- `order` — unique. **1 Order : 1 Payment**를 DB가 강제한다. 초기 범위는 부분·분할 결제를 열어두지 않는다
- `paymentKey` — 토스가 발급하는 결제 식별자. 조회·취소의 키이므로 저장하고 unique로 둔다
- `amount` — `DECIMAL(14,2)`. `Order.totalAmount`와 자릿수를 맞춘다
- `method` — 토스가 준 문자열을 그대로 저장한다. 새 결제 수단이 추가돼도 승인 반영이 실패하지 않게 enum으로 두지 않는다
- **승인에 성공한 결제만 저장한다.** 거절을 행으로 남기면 그 주문의 재시도가 `order` unique에 막힌다. 거절은 로그로 남긴다

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/payments/confirm` | 결제 승인 | 회원 |
| GET | `/api/payments/{orderNumber}` | 결제 정보 조회 | 회원 |

### POST `/api/payments/confirm` — 결제 승인

```
{ paymentKey, orderId, amount }

선점 트랜잭션
  1. orderNumber로 주문 조회, 소유자 검증            → 없거나 타인의 주문 O001
  2. amount == Order.totalAmount 확인                → 다르면 PAY004, 토스 호출 없음
  3. CAS PENDING → PAYMENT_IN_PROGRESS + paymentKey 저장
     └ 0건이면 현재 상태로 분기 (아래 "중복 요청")
커밋

토스 승인 API 호출 (트랜잭션 없음, 자동 재시도 없음)

반영 트랜잭션 (paymentKey까지 비교하는 CAS)
  성공        PAID + Payment(SUCCESS) 저장
  확정 거절    PENDING + paymentKey 비움, 거절 로그
  결과 불명    변경 없음 → 복구 배치가 판정
커밋
```

FE가 successUrl에서 받은 값으로 호출한다. 토스 승인을 한 번만 호출하고 그 결과를 주문에 반영한다.
경계와 판정의 근거는 [ADR 0026](../../decisions/purchase/0026-payment-confirm-outside-transaction.md)이 소유한다.

**금액 검증**
- `amount`는 리다이렉트 쿼리로 오는 값이라 사용자가 고칠 수 있다. 다르면 승인 API를 호출하지 않는다

**중복 요청** (3번 CAS가 0건일 때)

| 현재 상태 | 응답 |
|---|---|
| `PAYMENT_IN_PROGRESS` | `PAY003` 승인 진행 중 |
| `PAID` + 같은 `paymentKey` | 기존 결제 반환 — 응답을 잃은 FE의 재시도다 |
| `PAID` + 다른 `paymentKey` | `PAY002 PAYMENT_ALREADY_EXISTS` |
| 그 밖의 상태 | `PAY005` 승인 불가 |

**승인 거절**
- 한도 초과·카드사 거절처럼 토스가 거절을 응답한 경우다 → `PAY006`
- 재고는 점유된 채로 남고 사용자는 **결제창부터** 다시 시도한다. 끝내 결제되지 않으면 `PENDING` 만료 처리가 정리한다

**결과 불명**
- 타임아웃·응답 유실은 토스에서 승인됐을 수 있으므로 되돌리지 않는다. 사용자에게는 `PAY003` 승인 진행 중으로 응답한다

**응답** — 승인 성공, 같은 키의 재요청, 결제 조회가 같은 형식을 쓴다

```json
{ "orderNumber": "ORD-20261106-A1B2C3D4E5F6", "amount": 128000, "method": "카드", "status": "SUCCESS", "paidAt": "2026-11-06T10:15:30" }
```

결제 PK와 `paymentKey`는 내보내지 않는다. 결제는 `orderNumber`로 가리킨다

### GET `/api/payments/{orderNumber}` — 결제 정보 조회

```
1. 주문 조회, 소유자 검증            → 없거나 타인의 주문 O001
2. 그 주문의 결제 조회               → 없으면 PAY001
3. 응답 변환
```

자기 주문의 결제를 돌려준다. 성공한 결제만 저장하므로 승인 전이거나 거절된 주문은 `PAY001`이다.

## 환불

API가 따로 없다. 회원의 주문 취소(`POST /api/orders/{orderNumber}/cancel`)와 ADMIN의 상태 변경이 `PAID` 주문이면 이 흐름을 탄다.

```
선점 트랜잭션
  CAS PAID → REFUND_IN_PROGRESS              → 0건이면 상태로 거절 (O002 · O003 · O004)
커밋

토스 결제 취소 호출 (트랜잭션 없음)
  POST /v1/payments/{paymentKey}/cancel
  Idempotency-Key: cancel-{paymentKey}
  { cancelReason }                           cancelAmount 없음 = 전액 취소

반영 트랜잭션
  성공         CANCELLED + Payment(REFUNDED) + products 잠금 (id 오름차순) 후 재고 복구
  확정 거절    CAS → PAID, ERROR 로그          → 409 PAY007
  결과 불명    변경 없음 → 복구 배치가 판정     → 409 PAY008
커밋
```

토스 취소는 되돌릴 수 없어 트랜잭션 밖에서 부르고, 재고는 돈이 돌아간 뒤에 푼다.
판정과 근거는 [ADR 0029](../../decisions/purchase/0029-refund-outside-transaction.md)가 소유한다.

- 환불은 `PAID`인 주문만, 전액만 한다. `SHIPPING` 이후는 반품이라 범위 밖이다
- 토스 취소 요청 중(`REFUND_IN_PROGRESS`)인 주문은 다시 취소하거나 배송 처리할 수 없다
- 환불 시각은 응답의 `cancels[].canceledAt`이다

## 복구 배치

API가 아니라 스케줄 작업이다. 서버 장애나 응답 유실로 요청이 끝내지 못한 주문(`PAYMENT_IN_PROGRESS`·`REFUND_IN_PROGRESS`)을 토스 조회 결과로 바로잡는다.
판정과 종결 근거는 [ADR 0026](../../decisions/purchase/0026-payment-confirm-outside-transaction.md)·[0028](../../decisions/purchase/0028-payment-result-whitelist-and-recovery-limit.md)·[0029](../../decisions/purchase/0029-refund-outside-transaction.md)가, 실행 시간 상한과 판정하지 못한 주문의 처리는 [0030](../../decisions/purchase/0030-recovery-batch-runtime-limits.md)이 소유한다.

### 구조

```
PaymentRecoveryScheduler      @Scheduled 1분, 트랜잭션 없음
  ├─ recoverStuckOrders()     5분~1시간 멈춘 주문 판정, 실행당 50초 예산
  │    └─ PaymentRecoveryService.recover(id)        주문마다, 트랜잭션 없음
  │         ├─ 주문 읽기 (status, paymentKey 고정)
  │         ├─ 토스 조회 (트랜잭션 밖, 읽기 타임아웃 5초)
  │         └─ ApprovalTransactions / RefundTransactions   요청 경로와 같은 CAS
  └─ alertAbandoned()         이번에 1시간을 막 넘긴 주문만 한 번 ERROR
```

### 흐름

![결제 복구 배치 시퀀스](../../diagrams/images/seq-payment-recovery.png)

읽는 법과 Mermaid 원본: [seq-payment-recovery.md](../../diagrams/purchase/seq-payment-recovery.md).

### 수치

| 항목 | 값 | 이유 |
|---|---|---|
| 실행 주기 | 1분 (`fixedDelay`) | 앞 실행이 끝난 뒤 센다. 한 인스턴스 안에서는 겹치지 않는다 |
| 판정 대상 | `updated_at`이 5분 ~ 1시간 전 | 5분은 요청 최대 수명보다 길어야 한다. 1시간이 넘으면 수동 처리 |
| 한 번에 | 100건, 오래된 순 | `idx_orders_status_updated_at`을 탄다 |
| 실행 예산 | 50초 | 다음 주문을 시작하기 전에만 확인하고, 넘으면 남은 건은 다음 주기로 넘긴다. 처리 중인 주문은 끊지 않아 실제 상한은 50초 + 한 건(약 15초)이다 |
| 토스 조회 타임아웃 | 읽기 5초 | 조회는 실패해도 다음 주기에 다시 하면 된다. 승인·취소 호출의 30초와 별개다 |
| 반영 트랜잭션 타임아웃 | 10초 | 요청 최대 수명에 상한을 두기 위해서다 |
| 스케줄러 스레드 | 2 (`spring.task.scheduling.pool.size`) | 기본값 1이면 복구 배치가 토스 지연으로 길어질 때 `PENDING` 만료 배치까지 멈춘다 |

- **요청 최대 수명:** 커넥션 대기 30초 × 2(선점·반영) + 토스 호출(연결 3초 + 읽기 30초) + 반영 10초로 약 100초다. 5분은 이보다 넉넉하다. 수명이 바뀌면 판정 시간도 함께 바꾼다
- 5초와 50초는 토스 실제 응답 시간을 모르고 잡은 임시값이다. 테스트 키로 응답 시간을 확인한 뒤 조정한다

### 판정

**`PAYMENT_IN_PROGRESS` 주문** — 승인 반영과 같은 CAS (`status` + `payment_key`)

| 토스 조회 결과 | 처리 |
|---|---|
| `DONE` + `orderId`·금액 일치 | `PAID` + `Payment(SUCCESS)` 저장 |
| `DONE` + 불일치 | 변경 없음, WARN. 1시간 뒤 수동 처리 |
| `ABORTED`, `EXPIRED`, `NOT_FOUND_PAYMENT` | `PENDING` + `paymentKey` 비움 |
| `READY`, `IN_PROGRESS`, 조회 실패 | 변경 없음, 다음 주기에 다시 조회 |
| `CANCELED` 계열, `WAITING_FOR_DEPOSIT` | 변경 없음, WARN. 1시간 뒤 수동 처리 |

**`REFUND_IN_PROGRESS` 주문** — 환불 반영과 같은 CAS (`status`)

| 토스 조회 결과 | 처리 |
|---|---|
| `CANCELED` | `CANCELLED` + `Payment(REFUNDED)` + 재고 복구 |
| `DONE` | `PAID` 복귀. 취소되지 않았다 |
| `PARTIAL_CANCELED`, 그 밖 | 변경 없음, WARN. 1시간 뒤 수동 처리 |
| 조회 실패 | 변경 없음, 다음 주기에 다시 조회 |

### 규칙

- **반영은 요청 경로와 같은 메서드를 쓴다.** 요청과 배치가 같은 CAS를 타야 둘이 겹쳐도 한쪽만 반영된다. 여러 인스턴스가 동시에 돌아도 같다
- **조회에 쓴 `paymentKey`를 그대로 CAS에 넘긴다.** 반영 직전에 다시 읽으면 그사이 거절 → 재시도로 바뀐 키에 이전 결과가 반영된다
- **판정하지 못하면 행을 갱신하지 않는다.** `updated_at`이 바뀌면 1시간 종결 시계가 리셋되어 수동 처리로 넘어가지 않는다
- **반영 CAS가 0건이면 현재 상태를 확인한다.** 이미 목표 상태면 다른 경로가 먼저 반영한 것이다. 아니면 토스와 주문이 어긋난 것이라 ERROR를 남긴다
- **1시간 경보는 한 번만 낸다.** 직전 실행 이후 1시간 경계를 넘은 주문만 ERROR로 남긴다. 매 주기 반복하지 않는다. 인스턴스가 여러 대면 중복될 수 있다

**수동 처리:** 자동으로 취소하지 않는다. 돈이 빠져나갔을 수 있어서다. 운영자가 토스 개발자센터에서 `paymentKey`로 상태를 확인한다.
승인됐으면 `PAID`와 `Payment`를 DB에 반영하고, 승인되지 않았으면 `PENDING`으로 되돌리고 `paymentKey`를 비운다.

## 토스 연동

PG 호출은 `PaymentGateway` 인터페이스 뒤에 둔다. 구현은 토스이고, 테스트는 Mock 구현을 쓴다.

```java
public interface PaymentGateway {
    TossPayment confirm(String paymentKey, String orderId, BigDecimal amount);
    TossPayment getPayment(String paymentKey);
    TossPayment cancel(String paymentKey, String cancelReason);
}
```

서비스가 토스 코드를 몰라도 되게, 확정 거절과 결과 불명은 서로 다른 예외로 구분해 던진다.

### 호출하는 API

| 용도 | API | 호출하는 쪽 |
|---|---|---|
| 결제 승인 | `POST /v1/payments/confirm` | `POST /api/payments/confirm` |
| 결과 불명 판정 | `GET /v1/payments/{paymentKey}` | 복구 배치 |
| 환불 | `POST /v1/payments/{paymentKey}/cancel` | 주문 취소의 `PAID` 분기 |

- **연동 제품은 주문서형이다.** 키는 `test_gck`/`test_gsk` 세트이고 `orderId`로는 결제를 조회할 수 없어, 복구는 저장한 `paymentKey`로 한다
- 인증은 `Authorization: Basic base64(시크릿키 + ":")`다. 시크릿 키는 환경변수 `TOSS_SECRET_KEY`로만 받는다
- FE의 클라이언트 키와 백엔드의 시크릿 키는 같은 세트여야 한다. 섞으면 `INVALID_API_KEY`가 난다
- 승인은 인증 후 10분 안에 호출해야 한다
- 타임아웃은 연결 3초, 읽기 30초로 시작한다. 문서에 권장값이 없어 테스트 후 조정한다

### 요청

```
POST /v1/payments/confirm
Idempotency-Key: {paymentKey}
{ paymentKey, orderId, amount }

POST /v1/payments/{paymentKey}/cancel
Idempotency-Key: cancel-{paymentKey}
{ cancelReason }
```

`orderId`는 `Order.orderNumber`다. 멱등키는 에러 뒤 바꿔서 재시도하지 않는다. 중복 방어는 이 헤더에 기대지 않는다.

### 응답

승인·조회는 Payment 객체를 돌려준다. 우리가 쓰는 필드는 아래뿐이다.

| 토스 필드 | 용도 | 저장 |
|---|---|---|
| `paymentKey` | 결제 식별 | `payments.payment_key` |
| `orderId` | 다른 주문의 결제인지 검증 | 저장하지 않음. `orderNumber`와 대조 |
| `status` | 승인·실패 판정 | `DONE`이면 `Payment(SUCCESS)` |
| `totalAmount` | 금액 검증 | `payments.amount`. `BigDecimal`로 변환해 `compareTo`로 비교 |
| `method` | 결제 수단 | `payments.method` |
| `approvedAt` | 승인 시각 | `payments.paid_at` |

승인 성공도 `orderId`와 금액이 주문과 일치할 때만 `PAID`로 전이한다.

실패 응답은 `{ code, message }`다. 코드별 판정은 [ADR 0028](../../decisions/purchase/0028-payment-result-whitelist-and-recovery-limit.md)이 소유한다. 목록에 없는 코드는 결과 불명이다.

## 에러 코드

구현 때 `PaymentErrorCode`와 [error-codes.md](../../conventions/error-codes.md)에 추가한다.

| 코드 | 상수 | HTTP | 언제 |
|---|---|---|---|
| PAY003 | `PAYMENT_IN_PROGRESS` | 409 | 승인 진행 중, 결과 불명 |
| PAY004 | `PAYMENT_AMOUNT_MISMATCH` | 400 | 요청 금액이 주문 금액과 다름 |
| PAY005 | `PAYMENT_NOT_ALLOWED` | 409 | 승인할 수 없는 주문 상태 |
| PAY006 | `PAYMENT_REJECTED` | 402 | 토스의 승인 거절 |
| PAY007 | `PAYMENT_REFUND_REJECTED` | 409 | 토스의 환불 거절 |
| PAY008 | `PAYMENT_REFUND_IN_PROGRESS` | 409 | 환불 결과 불명 |

## 착수 전 정할 것

- **주문서형 키로 `paymentKey` 조회와 취소가 되는지 테스트 키로 확인한다** — 막혀 있으면 복구 배치와 환불이 성립하지 않는다
- **테스트 키로 확인한다** — 같은 `paymentKey`로 승인을 다시 호출했을 때의 응답, 실제 응답 시간, `method` 값
- **같은 `orderId`의 재결제를 토스 테스트 환경에서 확인한다** — 승인 거절 뒤 재시도의 전제인데 공식 문서에 허용 여부가 없다.
  막혀 있으면 토스 `orderId`를 시도마다 새로 발급하도록 `orderNumber`와 분리해야 한다

# payment — 결제

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `PaymentErrorCode`만 선반영돼 있다.

[order.md](order.md)의 주문 하나에 결제 하나가 붙는다. 셋에 걸친 엔티티 관계는
[README.md](README.md)가 소유한다.

**PG는 토스페이먼츠다.** 결제창 호출은 FE가 토스 SDK로 하고, 백엔드는 **승인과 결과 반영**을 맡는다.
카드 입력·인증은 토스 결제창 안에서 끝나며 카드 데이터는 백엔드를 지나지 않는다.

## 결제 전체 흐름

```
FE     POST /api/orders                    → { orderNumber, amount }
FE     토스 SDK 결제창 호출                  orderId = orderNumber, amount, successUrl, failUrl
토스   인증 성공 → successUrl (FE 페이지)    ?paymentKey&orderId&amount
FE     POST /api/payments/confirm          승인과 결과 반영
토스   인증 실패·취소 → failUrl (FE 페이지)  백엔드 호출 없음
```

**successUrl·failUrl은 FE 페이지다.** 액세스 토큰이 `Authorization` 헤더로만 실리므로 토스의 리다이렉트는
인증되지 않는다. 백엔드 엔드포인트를 리다이렉트 대상으로 두지 않는다.

**토스 `orderId`는 `Order.orderNumber`다.** PK가 아니다 — 토스 `orderId`는 6자 이상이고, PK를 쓰면 주문 순번이 노출된다.

**failUrl은 주문을 바꾸지 않는다.** 사용자가 결제창을 닫은 경우까지 포함하므로 즉시 취소하면 재시도할 수 없다.
주문은 `PENDING`, 재고는 점유된 채로 남고 같은 주문으로 다시 시도한다.
끝내 결제되지 않은 주문은 [order.md](order.md)의 `PENDING` 만료 처리가 정리한다.

## 엔티티

id, order, paymentKey, amount, method(CARD/TRANSFER), status(SUCCESS/REFUNDED), paidAt

- `order` — unique. **1 Order : 1 Payment**를 DB가 강제한다. 초기 범위는 부분·분할 결제를 열어두지 않는다
- `paymentKey` — 토스가 발급하는 결제 식별자. 조회·취소의 키이므로 저장하고 unique로 둔다
- **승인에 성공한 결제만 저장한다.** 거절을 행으로 남기면 그 주문의 재시도가 `order` unique에 막힌다. 거절은 로그로 남긴다

PG 호출은 `PaymentGateway` 인터페이스 뒤에 둔다. 구현은 토스이고, 테스트는 Mock 구현을 쓴다.

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/payments/confirm` | 결제 승인 | 회원 |
| GET | `/api/payments/{orderId}` | 결제 정보 조회 | 회원 |
| POST | `/api/payments/{id}/refund` | 환불 요청 | 회원 |

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

### GET `/api/payments/{orderId}` — 결제 정보 조회

```
1. 주문 조회, 소유자 검증            → 없거나 타인의 주문 O001
2. 그 주문의 결제 조회               → 없으면 PAY001
3. 응답 변환
```

자기 주문의 결제를 돌려준다. 성공한 결제만 저장하므로 승인 전이거나 거절된 주문은 `PAY001`이다.

### POST `/api/payments/{id}/refund` — 환불 요청

```
1. 결제 조회, 소유자 검증            → 없거나 타인의 결제 PAY001
2. 토스 결제 취소 호출
3. Payment(REFUNDED), Order CANCELLED, 재고 복구
```

결제를 취소하고 점유했던 재고를 돌려놓는다. `CANCELLED` 전환이므로 재고 복구는 [order.md](order.md)의 규칙을 따른다.
2~3번의 **순서와 실패 처리는 아직 정해지지 않았다** — 아래 "착수 전 정할 것".

## 복구 배치

API가 아니라 스케줄 작업이다.

```
1. PAYMENT_IN_PROGRESS로 판정 시간 넘게 멈춘 주문 조회
2. 저장된 paymentKey로 토스 결제 조회
3. 결과 반영 (승인 API의 반영과 같은 CAS)
   DONE                PAID + Payment(SUCCESS) 저장
   ABORTED, EXPIRED    PENDING + paymentKey 비움
   그 밖, 조회 실패     변경 없음, 다음 주기에 다시 조회
```

서버 장애 등으로 결과가 반영되지 못한 주문을 토스 기준으로 바로잡는다. 판정 시간은 요청 최대 수명보다 길게 잡는다 —
[ADR 0026](../../decisions/purchase/0026-payment-confirm-outside-transaction.md).

## 에러 코드

구현 때 `PaymentErrorCode`와 [error-codes.md](../../conventions/error-codes.md)에 추가한다.

| 코드 | 상수 | HTTP | 언제 |
|---|---|---|---|
| PAY003 | `PAYMENT_IN_PROGRESS` | 409 | 승인 진행 중, 결과 불명 |
| PAY004 | `PAYMENT_AMOUNT_MISMATCH` | 400 | 요청 금액이 주문 금액과 다름 |
| PAY005 | `PAYMENT_NOT_ALLOWED` | 409 | 승인할 수 없는 주문 상태 |
| PAY006 | `PAYMENT_REJECTED` | 402 | 토스의 승인 거절 |

## 착수 전 정할 것

- **같은 `orderId`의 재결제를 토스 테스트 환경에서 확인한다** — 승인 거절 뒤 재시도의 전제인데 공식 문서에 허용 여부가 없다.
  막혀 있으면 토스 `orderId`를 시도마다 새로 발급하도록 `orderNumber`와 분리해야 한다
- **가상계좌 지원 여부** — 지원하면 승인 뒤 입금까지 일 단위로 걸린다. 입금 대기 상태와
  결제 수단별 `PENDING` 만료 정책이 필요하다
- 환불 시 토스 취소 호출과 결제 상태·주문 상태·재고 갱신의 **순서**, 토스 취소 실패 처리를 명확히 정의
- 환불 API와 `PAID` 주문의 취소 API(`POST /api/orders/{id}/cancel`)의 관계, 환불 가능한 주문 상태

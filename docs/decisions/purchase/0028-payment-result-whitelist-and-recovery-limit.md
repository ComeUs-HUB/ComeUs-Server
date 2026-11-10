# ADR 0028 — 승인 거절은 확인된 코드만 인정하고 복구는 1시간 뒤 수동으로 넘긴다

- 상태: 채택 (0029·0030이 보강)
- 일자: 2026-11-06
- 관련: [ADR 0026](0026-payment-confirm-outside-transaction.md), [domains/purchase/payment.md](../../domains/purchase/payment.md), PR #16 리뷰

## 맥락

[ADR 0026](0026-payment-confirm-outside-transaction.md)은 승인 결과를 성공, 확정 거절, 결과 불명으로 나눴다.
구현 전에 [토스 에러 코드 문서](https://docs.tosspayments.com/reference/error-codes)와 대조하니 세 군데가 부족했다.

- **"4xx는 거절"이 틀렸다.** `PROVIDER_ERROR`와 `CARD_PROCESSING_ERROR`는 400이지만 일시 오류이고, `ALREADY_PROCESSING_REQUEST`는 처리 중이다.
  시크릿 키 오류까지 카드 거절로 처리되면 모든 사용자가 "거절"을 받고, `paymentKey`를 지워 복구 수단도 잃는다
- **복구 배치에 종결 조건이 없다.** 토스 조회가 계속 판정되지 않으면 취소도 막힌 채 재고가 영구히 묶인다
- **복구 배치가 응답을 검증하지 않는다.** 사용자가 다른 주문의 `paymentKey`를 보내면 조회가 `DONE`을 돌려줘, 돈을 내지 않은 주문이 `PAID`가 될 수 있다

## 결정

**확인된 코드만 확정 거절로 보고 나머지는 전부 결과 불명으로 둔다. 1시간이 지나도 판정되지 않으면 자동으로 처리하지 않고 수동으로 넘긴다.**

### 승인 응답 판정

목록에 없는 코드는 결과 불명이다. 토스가 새 코드를 추가해도 안전한 쪽으로 처리되고, 틀렸을 때의 비용도 작다 —
불명을 거절로 보면 중복 청구가 될 수 있지만, 거절을 불명으로 보면 복구 배치가 돌 때까지 재시도가 늦어질 뿐이다.

| 판정 | 처리 |
|---|---|
| 확정 거절 | `PENDING` 복귀, `paymentKey` 비움, `PAY006` |
| 결과 불명 | 상태 유지, `PAY003`, 복구 배치가 판정 |
| 결과 불명 + 경보 | 위와 같고 ERROR 로그 |

**확정 거절 (31개)**

| 분류 | 코드 |
|---|---|
| 카드·계좌 | `REJECT_CARD_PAYMENT` `REJECT_CARD_COMPANY` `REJECT_ACCOUNT_PAYMENT` `REJECT_TOSSPAY_INVALID_ACCOUNT` `INVALID_REJECT_CARD` `INVALID_STOPPED_CARD` `INVALID_CARD_LOST_OR_STOLEN` `INVALID_CARD_EXPIRATION` `INVALID_CARD_NUMBER` `INVALID_PASSWORD` `INVALID_ACCOUNT_INFO_RE_REGISTER` `RESTRICTED_TRANSFER_ACCOUNT` `FDS_ERROR` `EXCEED_MAX_AUTH_COUNT` |
| 한도 | `EXCEED_MAX_DAILY_PAYMENT_COUNT` `EXCEED_MAX_PAYMENT_AMOUNT` `EXCEED_MAX_ONE_DAY_WITHDRAW_AMOUNT` `EXCEED_MAX_ONE_TIME_WITHDRAW_AMOUNT` `EXCEED_MAX_AMOUNT` `EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT` `EXCEED_MAX_ONE_DAY_AMOUNT` |
| 할부·수단 | `EXCEED_MAX_CARD_INSTALLMENT_PLAN` `NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT` `INVALID_CARD_INSTALLMENT_PLAN` `NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN` `NOT_ALLOWED_POINT_USE` `BELOW_MINIMUM_AMOUNT` `NOT_AVAILABLE_PAYMENT` `NOT_AVAILABLE_BANK` |
| 세션 없음 | `NOT_FOUND_PAYMENT` `NOT_FOUND_PAYMENT_SESSION` |

세션 없음을 거절로 두는 이유는 위조된 `paymentKey`로 주문이 승인 중에 묶이지 않게 하려는 것이다.

**결과 불명 (9개)** — 5xx 전체, 타임아웃, 응답 유실, 해석할 수 없는 응답과 아래 코드

`ALREADY_PROCESSED_PAYMENT` `ALREADY_PROCESSING_REQUEST` `PROVIDER_ERROR` `CARD_PROCESSING_ERROR` `UNAPPROVED_ORDER_ID`
`FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING` `FAILED_INTERNAL_SYSTEM_PROCESSING` `FAILED_CARD_COMPANY_RESPONSE` `UNKNOWN_PAYMENT_ERROR`

**결과 불명 + 경보 (9개)** — 사용자가 고칠 수 없는 우리 쪽 설정·요청 오류

`INVALID_API_KEY` `UNAUTHORIZED_KEY` `INCORRECT_BASIC_AUTH_FORMAT` `INVALID_REQUEST` `FORBIDDEN_REQUEST`
`NOT_FOUND_TERMINAL_ID` `INVALID_UNREGISTERED_SUBMALL` `NOT_REGISTERED_BUSINESS` `INVALID_AUTHORIZE_AUTH`

### 응답 검증

승인 성공과 조회 `DONE`은 **`orderId`와 금액이 주문과 일치할 때만** `PAID`로 전이한다. 다르면 전이하지 않고 ERROR 로그를 남긴다.

### 복구 배치 판정

| 토스 조회 결과 | 처리 |
|---|---|
| `DONE` + 검증 통과 | 성공 반영과 같은 전이 |
| `DONE` + 검증 실패 | 상태 유지, 경보 |
| `ABORTED`, `EXPIRED`, `NOT_FOUND_PAYMENT` | 거절 반영과 같은 전이 |
| `READY`, `IN_PROGRESS` | 상태 유지. 토스에서 만료되면 다음 조회로 정리된다 |
| `CANCELED`, `PARTIAL_CANCELED`, `WAITING_FOR_DEPOSIT` | 상태 유지, 경보 |
| 조회 실패 | 상태 유지, 다음 주기에 재시도 |

### 종결 조건

`PAYMENT_IN_PROGRESS`로 **1시간** 넘게 판정되지 않은 주문은 복구 조회에서 빼고 ERROR 로그를 남겨 수동 처리 대상으로 넘긴다.

- 자동으로 취소하지 않는다. 돈이 빠져나갔을 수 있어 재고를 풀면 초과 판매와 미배송으로 이어진다
- 조회에서 빼는 이유는 판정되지 않는 주문이 오래된 순 조회의 앞을 차지해 정상 복구를 막지 않게 하려는 것이다
- 1시간은 토스 문서의 만료 시간(10분과 30분이 함께 적혀 있다)보다 넉넉하게 잡은 값이다
- 수동 처리는 운영자가 토스 개발자센터에서 상태를 확인하고 DB로 처리한다. 절차는 [payment.md](../../domains/purchase/payment.md)가 소유한다
- 경보는 ERROR 로그로 시작한다. 알림 인프라는 Phase 7에 붙인다

### 토스 멱등 헤더

승인 호출에 `Idempotency-Key: {paymentKey}`를 보낸다. [ADR 0026](0026-payment-confirm-outside-transaction.md)을 쓸 때는 확인하지 못했는데,
[토스 문서](https://docs.tosspayments.com/reference/using-api/authorization)가 모든 POST API에 지원한다고 적고 있다.
승인 API를 콕 집은 문장은 없어, 중복 방어는 계속 CAS와 unique가 맡는다. 문서가 위험하다고 안내하는 대로 에러 뒤 키를 바꿔 재시도하지 않는다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 4xx 전체를 거절로 두고 예외만 나열 | 모르는 코드가 거절이 되어 `paymentKey`를 지운다. 틀렸을 때의 비용이 큰 쪽이 기본값이 된다 |
| 1시간 뒤 자동 취소 | 승인됐을 수 있는 주문의 재고를 푼다. 재고를 잃는 것보다 돈과 주문이 어긋나는 쪽이 비싸다 |
| 재고만 묶은 채 계속 재조회 | 판정되지 않는 주문이 쌓이면 정상 복구까지 밀리고, 사람이 알 방법이 없다 |
| 지금 ADMIN 수동 처리 API 구현 | 발생 빈도를 모르는 경로다. 운영 절차로 시작하고 빈도가 확인되면 만든다 |

## 결과 / 되돌리려면

- [ADR 0026](0026-payment-confirm-outside-transaction.md)의 "4xx는 거절" 판정과 "다음 주기에 다시 본다"를 이 ADR이 대체한다. 구조는 그대로다
- 코드 목록은 2026-11-06 기준 문서다. 토스가 코드를 추가하면 결과 불명으로 처리되므로 안전하지만, 거절 코드라면 목록에 더해야 사용자가 바로 재시도할 수 있다
- 결과 불명이 늘면 사용자는 복구 배치가 돌 때까지 재시도하지 못한다. 측정에서 빈도를 본다
- 수동 처리 대상이 자주 생기면 ADMIN API를 만들고, 그때 이 ADR을 보강한다

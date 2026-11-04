# payment — 결제

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `PaymentErrorCode`만 선반영돼 있다.

[order.md](order.md)의 주문 하나에 결제 하나가 붙는다. 셋에 걸친 엔티티 관계는
[README.md](README.md)가 소유한다.

**PG는 토스페이먼츠다.** 결제창 호출은 FE가 토스 SDK로 하고, 백엔드는 **승인과 결과 반영**을 맡는다.
카드 입력·인증은 토스 결제창 안에서 끝나며 카드 데이터는 백엔드를 지나지 않는다.

## 흐름

```
POST /api/orders                  → { orderNumber, amount }
FE  토스 SDK 결제창 호출           orderId = orderNumber, amount, successUrl, failUrl
토스 → successUrl (FE 페이지)      ?paymentKey&orderId&amount
FE  POST /api/payments/confirm    백엔드가 금액을 검증하고 토스 승인 API를 호출한 뒤 결과를 반영한다
토스 → failUrl (FE 페이지)         백엔드 호출 없음. 주문은 PENDING 그대로
승인 거절                         주문을 PENDING으로 되돌린다. 사용자는 결제창부터 다시 시도한다
```

## 엔티티

id, order, paymentKey, amount, method(CARD/TRANSFER), status(SUCCESS/REFUNDED), paidAt

- `order` — unique. 1 Order : 1 Payment를 DB가 강제한다
- **승인에 성공한 결제만 저장한다.** 거절을 행으로 남기면 그 주문의 재시도가 `order` unique에 막힌다. 거절은 로그로 남긴다
- `paymentKey` — 토스가 발급하는 결제 식별자. 조회·취소의 키이므로 저장하고 unique로 둔다

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/payments/confirm` | 결제 승인 — FE가 successUrl에서 받은 `paymentKey`·`orderId`·`amount`로 호출 | 회원 |
| GET | `/api/payments/{orderId}` | 결제 정보 조회 | 회원 |
| POST | `/api/payments/{id}/refund` | 환불 요청 | 회원 |

## 규칙

- **1 Order : 1 Payment** — 초기 범위는 부분·분할 결제를 열어두지 않는다
- **successUrl·failUrl은 FE 페이지다.** 액세스 토큰이 `Authorization` 헤더로만 실리므로 토스의 리다이렉트는
  인증되지 않는다. 백엔드 엔드포인트를 리다이렉트 대상으로 두지 않는다
- **토스 `orderId`는 `Order.orderNumber`다.** PK가 아니다 — 토스 `orderId`는 6자 이상이고, PK를 쓰면 주문 순번이 노출된다
- 승인 요청의 주문이 요청자의 것이 아니면 → `O001 ORDER_NOT_FOUND`
- **승인 전에 금액을 검증한다.** 요청 `amount`가 `Order.totalAmount`와 다르면 승인 API를 호출하지 않는다.
  `amount`는 리다이렉트 쿼리로 오는 값이라 사용자가 고칠 수 있다
- 승인 성공 → `Payment(SUCCESS)` 저장과 Order `PAID` 전환을 한 트랜잭션에서 한다
- 이미 결제된 주문 → `PAY002 PAYMENT_ALREADY_EXISTS`. 단 **같은 `paymentKey`로 다시 온 승인 요청은 기존 결제를 반환한다** —
  응답을 잃은 FE의 재시도다
- **결제창 단계의 실패·취소(failUrl)는 주문을 바꾸지 않는다.** 주문은 `PENDING`, 재고는 점유된 채로 남고
  사용자는 같은 주문으로 결제를 다시 시도한다. failUrl은 사용자가 결제창을 닫은 경우까지 포함하므로
  즉시 취소하면 재시도할 수 없다. 끝내 결제되지 않은 주문은 [order.md](order.md)의 `PENDING` 만료 처리가 정리한다
- **승인 거절은 주문을 `PENDING`으로 되돌린다.** 한도 초과·카드사 거절처럼 토스가 거절을 응답한 경우다.
  재고는 점유된 채로 남고 사용자는 결제창부터 다시 시도한다. 끝내 결제되지 않으면 `PENDING` 만료 처리가 정리한다
- **결과를 모르는 실패는 되돌리지 않는다.** 타임아웃·응답 유실은 토스에서 승인됐을 수 있으므로 승인 진행 상태로 두고
  복구 경로가 토스 조회로 판정한다. 이때 `PENDING`으로 되돌리면 재시도가 중복 청구가 될 수 있다
- PG 호출은 `PaymentGateway` 인터페이스 뒤에 둔다. 구현은 토스이고, 테스트는 Mock 구현을 쓴다

## 착수 전 정할 것

- **승인 경계** — 승인 API 호출을 트랜잭션 밖에 두고, 주문 상태 선점(`PENDING` → 승인 진행 상태)으로 승인 진입을
  한 번만 허용하며, `payments.order_id` unique를 최후 방어선으로 둔다. 승인 후 반영 전에 멈춘 주문을
  복구하는 경로(웹훅·토스 조회 배치)와 함께 **ADR로 남긴다**([decisions/](../../decisions/)).
  승인 진행 상태는 [order.md](order.md)의 상태 전환표에 들어간다
- **같은 `orderId`의 재결제를 토스 테스트 환경에서 확인한다** — 승인 거절 뒤 재시도의 전제인데 공식 문서에 허용 여부가 없다.
  막혀 있으면 토스 `orderId`를 시도마다 새로 발급하도록 `orderNumber`와 분리해야 한다
- **거절과 결과 불명을 가르는 기준** — 토스 문서에 구분 안내가 없다. 어떤 응답을 확정 거절로 볼지 정한다
- **복구 조회의 키** — 승인 호출 전에 `paymentKey`를 저장할지 정한다. orderId 조회는 결제위젯 시크릿 키로 호출할 수 없다
- **가상계좌 지원 여부** — 지원하면 승인 뒤 입금까지 일 단위로 걸린다. 입금 대기 상태와
  결제 수단별 `PENDING` 만료 정책이 필요하다
- 승인 진행 중·금액 불일치·승인 거절의 에러 코드 — [error-codes.md](../../conventions/error-codes.md)에 추가한다
- 환불 시 결제 상태·주문 상태·재고를 갱신하는 **순서**를 명확히 정의

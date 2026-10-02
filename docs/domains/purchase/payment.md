# payment — 결제

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../../roadmap.md)가 소유한다.
> `PaymentErrorCode`만 선반영돼 있다.

[order.md](order.md)의 주문 하나에 결제 하나가 붙는다. 셋에 걸친 엔티티 관계는
[purchase.md](README.md)가 소유한다.

## 엔티티

id, order, amount, method(CARD/TRANSFER), status(SUCCESS/FAILED/REFUNDED), paidAt

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/payments` | 결제 요청 | 회원 |
| GET | `/api/payments/{orderId}` | 결제 정보 조회 | 회원 |
| POST | `/api/payments/{id}/refund` | 환불 요청 | 회원 |

## 규칙

- **1 Order : 1 Payment** — 초기 범위는 부분·분할 결제를 열어두지 않는다
- 결제 성공 → Order status `PAID` 전환
- 결제 실패 → 재고 복구
- 중복 결제 시도 → `PAY002 PAYMENT_ALREADY_EXISTS`
- 외부 PG 연동은 `PaymentGateway` 인터페이스로 추상화하고 **우선 Mock으로 구현**한다.
  실제 PG(포트원 등) 연동 여부는 미결이다 — [roadmap.md](../../roadmap.md)

## 착수 전 정할 것

- 중복 결제 방어를 서비스 계층에서 할지 DB 유니크 제약으로 할지 (둘 다 두는 것도 선택지)
- 환불 시 결제 상태·주문 상태·재고를 갱신하는 **순서**를 명확히 정의

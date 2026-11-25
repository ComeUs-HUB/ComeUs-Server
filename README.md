# ComeUs Backend

대용량 트래픽을 목표로 한 이커머스 백엔드입니다.
장바구니, 주문, 결제 과정에서 동시 요청에도 재고와 결제가 어긋나지 않도록 설계하고, 설계 선택을 측정치로 검증합니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| 언어·프레임워크 | Java 21, Spring Boot 3.5, Spring Security |
| 데이터 | MySQL 8, Spring Data JPA, Flyway |
| 인증 | 카카오 OAuth, JWT |
| 테스트 | JUnit 5, H2, MySQL 실기동 검증 |
| 결제 | 토스페이먼츠 결제위젯 · 승인·조회·취소 API |
| 배포 | Docker, docker-compose, CI/CD, Actuator (예정) |
| 부하 측정 | k6 또는 Gatling (예정) |
| 동시성 전환 후보 | 원자적 감소, Redis 분산 락, Redis 재고 게이트 (예정) |

## 전체 아키텍처

![시스템 구성도](docs/diagrams/images/system-overview.png)

자세한 설명은 [시스템 구성도 문서](docs/diagrams/global/system-overview.md)에 있습니다.

도메인 구성: User, Auth, Category, Product, Cart, Order, Payment, Review

## 핵심 도메인 아키텍처: 주문 & 결제

### 전체 흐름

재고는 주문 생성에서 확보하고, 돈은 선점에 성공한 승인 한 번으로 확정합니다. ([설명](docs/diagrams/purchase/seq-purchase-overview.md))

![주문 · 결제 전체 흐름](docs/diagrams/images/seq-purchase-overview.png)

| 흐름 | 한 줄 요약 | 그림 |
|---|---|---|
| 주문 생성 | `carts` → `products`(id 순)를 잠그고 재고를 차감, 같은 멱등키 재요청에는 기존 주문을 돌려줌 | [주문 생성](docs/diagrams/purchase/seq-order-create.md) |
| 결제 승인 | 선점 CAS로 한 요청만 토스를 부르고, 토스 호출은 트랜잭션 밖. 결과를 모르면 상태를 두고 배치에 맡김 | [결제 승인](docs/diagrams/purchase/seq-payment-confirm.md) |
| 환불 | 승인과 같은 선점 → 토스 취소 → 반영. 재고는 토스 취소가 확인된 뒤에만 복구 | [환불](docs/diagrams/purchase/seq-refund.md) |
| 복구 배치 | 결과를 모른 채 멈춘 승인·환불 주문을 토스 조회 결과로 바로잡음. 1시간 넘으면 수동 처리 경보 | [결제 복구 배치](docs/diagrams/purchase/seq-payment-recovery.md) |

### 주문 상태

재고는 `PENDING`에서 점유하고, `CANCELLED`로 가는 모든 전이가 복구합니다. ([설명](docs/diagrams/purchase/state-order.md))

![주문 상태](docs/diagrams/images/state-order.png)

### 핵심 설계 결정

| 문제 | 해결 | 근거 |
|---|---|---|
| 재고 초과 판매 | 비관적 락을 기준선으로 시작하고 전환은 측정 후 판단 | [ADR 0025](docs/decisions/purchase/0025-pessimistic-lock-as-stock-concurrency-baseline.md) |
| 중복 주문 | 헤더 멱등키와 복합 유니크 | [ADR 0024](docs/decisions/purchase/0024-order-idempotency-key-in-header.md), [0027](docs/decisions/purchase/0027-idempotency-uk-violation-responds-conflict.md) |
| 중복 청구, 승인 후 장애 | 토스 호출을 트랜잭션 밖에 두고 상태 CAS와 복구 배치로 메움 | [ADR 0026](docs/decisions/purchase/0026-payment-confirm-outside-transaction.md) |
| 돈이 나갔는지 모르는 응답 | 확인된 거절 코드만 거절로 인정하고, 나머지는 결과 불명으로 두고 토스 조회로 판정 | [ADR 0028](docs/decisions/purchase/0028-payment-result-whitelist-and-recovery-limit.md) |
| 환불 거절 시 재고 이중 판매 | 토스 취소가 확인된 반영 트랜잭션에서만 재고 복구 | [ADR 0029](docs/decisions/purchase/0029-refund-outside-transaction.md) |
| 배치가 낡은 판단으로 덮어쓰기(ABA) | 배치 반영 CAS에 읽은 `updated_at`을 조건으로 추가, 실행 시간 상한 | [ADR 0030](docs/decisions/purchase/0030-recovery-batch-runtime-limits.md) |
| 결제 안 된 주문의 재고 점유 | 15분 만료 배치 | [order.md](docs/domains/purchase/order.md) |

### 검증

자동 테스트(H2) 외에 MySQL 8에서 직접 확인했습니다. 자세한 결과와 확인하지 못한 항목은 [로드맵](docs/roadmap.md)에 있습니다.

- **재고 동시성:** 재고 10에 동시 주문 200건 → 성공 10, 재고 부족 190, 최종 재고 0
- **중복 주문:** 같은 멱등키 동시 5건 → 주문 1건, 재고 1회 차감
- **중복 승인:** 같은 승인 요청 동시 2건 → 토스 호출 1회, 나머지는 즉시 `PAY003`
- **복구 배치:** 토스 기준 승인·미취소·취소 상태를 만든 주문을 각각 `PAID`·`PAID`·`CANCELLED`로 반영 (토스 테스트 키)

## 문서

| 문서 | 내용 |
|---|---|
| [로드맵](docs/roadmap.md) | 지금 어디까지 됐고 다음은 무엇인가 |
| [아키텍처](docs/architecture.md) | 패키지 구조, 의존 규칙, 인증 배선 |
| [다이어그램](docs/diagrams/README.md) | 시퀀스·상태·구성도와 각 그림이 답하는 질문 |
| [도메인 계약](docs/domains/) | 도메인별 API와 규칙 |
| [개발 규약](docs/conventions/) | 응답 포맷, 에러 코드, git 규칙 |
| [결정 기록(ADR)](docs/decisions/) | 왜 그렇게 정했는가 |
| [배포](docs/deployment.md) | 배포 방식과 운영 설정 |
| [로컬 실행](docs/local-setup.md) | 로컬 환경 세팅과 실행 방법 |
| [문서 안내](docs/README.md) | 전체 문서 구조 |

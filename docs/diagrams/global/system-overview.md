# 시스템 구성도

- **답하는 질문**: 시스템은 무엇과 통신하고, 안에 어떤 도메인이 있는가
- **범위**: 외부 시스템 · 도메인 · 도메인 간 참조. 클래스 수준은 [요청 처리 파이프라인](request-pipeline.md)과 시퀀스가 맡는다
- **원천**: [architecture.md](../../architecture.md) "의존 규칙" · [deployment.md](../../deployment.md) · ADR [0001](../../decisions/auth/0001-kakao-only-oauth.md) · [0008](../../decisions/platform/0008-flyway-owns-schema.md) · [0014](../../decisions/catalog/0014-category-reads-product-repository.md) · [0031](../../decisions/purchase/0031-payment-uses-order-repository-for-state-cas.md)

![시스템 구성도](../images/system-overview.png)

<details>
<summary>Mermaid 원본 — 내용은 이 블록이 기준이고 PNG는 여기서 다시 그린다</summary>

```mermaid
flowchart LR
    FE["FE (브라우저)<br/>access token은 메모리<br/>refresh token은 HttpOnly 쿠키"]
    Kakao["카카오<br/>인가 · 토큰 · 사용자 정보 · 연결 해제"]
    Toss["토스페이먼츠<br/>결제창 · 승인 · 조회 · 취소"]
    DB[("MySQL<br/>스키마는 Flyway V1~V6")]

    subgraph BE["백엔드 (Spring Boot)"]
        direction LR
        subgraph global["global"]
            Sec["security<br/>JWT 필터 · 401/403 처리"]
            Ex["exception · response<br/>ApiResponse · ErrorResponse"]
        end
        subgraph domain["domain"]
            direction TB
            subgraph built[" "]
                direction LR
                Auth[auth]
                User[user]
                Cart[cart]
                Product[product]
                Category[category]
                Order[order]
                Payment[payment]
            end
            subgraph next["계획"]
                direction LR
                Review[review]:::planned
            end
        end
    end

    Deploy["Docker · CI/CD · 모니터링"]:::planned

    FE -- "REST · Bearer" --> BE
    FE -. "로그인 시 302 왕복" .- Kakao
    FE -- "결제창" --> Toss
    Auth -- "REST" --> Kakao
    Payment -- "REST" --> Toss
    BE -- "JPA" --> DB
    Sec --> User

    Auth --> User
    User --> Auth
    Product --> Category
    Category -- "CA004 삭제 차단만" --> Product
    Cart --> User
    Cart --> Product

    Order --> Cart
    Order --> Product
    Order --> User

    Payment -- "CAS" --> Order
    Order -- "환불" --> Payment
    Review -.-> Product
    Review -.-> User
    Review -.-> Order
    Deploy -.-> BE

    classDef planned stroke-dasharray: 5 5,color:#888
    style next stroke-dasharray: 5 5
```

</details>

## 읽는 법

- **화살표는 "참조한다"**다. 도메인 간 참조는 상대 Service를 거친다. 예외는 둘이다
  - `category → product` — Service를 거치면 생성자 주입이 순환해 `ProductRepository`를 직접 읽는다
  - `payment → order`(CAS) — 결제의 상태 CAS는 지킬 규칙이 쿼리 조건 자체라 `OrderRepository`로 직접 실행한다. 재고 복구는 `OrderService`를 거친다
- `order → payment`(환불)는 `PAID` 주문 취소가 `OrderCancelService → RefundService`로 환불을 부르는 것이다
- `auth ↔ user`가 양방향인 것은 의도다 — 로그인은 회원을 찾거나 만들고(`auth → user`),
  탈퇴는 토큰 폐기와 카카오 연결 해제까지 끝내야 한다(`user → auth`)
- `security → user`는 JWT 필터가 **매 요청 DB에서 회원과 role을 읽기** 때문이다. 권한이 토큰 claim에 들어 있지 않다
- 점선은 Phase 5 Review와 Phase 7 배포다. 참조 방향은 architecture.md에 미리 정해 둔 것이다

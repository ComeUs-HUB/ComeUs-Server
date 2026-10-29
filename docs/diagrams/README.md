# 다이어그램

지금까지 만든 것을 그림으로 요약한다. **그림은 계약이 아니다** — 각 장이 가리키는 원천 문서가 계약을 소유하고,
그림과 문서가 어긋나면 문서를 따른다. 진행 상태(✅/⬜)는 [roadmap.md](../roadmap.md)만 적는다.

| 그림 | 답하는 질문 | 원천 |
|---|---|---|
| [시스템 구성도](global/system-overview.md) | 시스템은 무엇과 통신하고, 안에 어떤 도메인이 있는가 | [architecture.md](../architecture.md) |
| [요청 처리 파이프라인](global/request-pipeline.md) | 요청 하나가 어떤 단계를 거쳐 성공·에러 응답이 되는가 | [api-response.md](../conventions/api-response.md) |
| [카카오 로그인](auth/seq-auth-login.md) | 로그인부터 첫 API 호출까지 토큰이 어디서 만들어져 어디에 실리는가 | [auth.md](../domains/auth.md) |
| [OAuth · JWT 패키지 구조](auth/package-auth.md) | 로그인에 참여하는 클래스가 어느 패키지에 있고, 의존은 어느 방향으로만 흐르는가 | [auth.md](../domains/auth.md) |
| [상품 목록 조회](catalog/seq-product-list.md) | 필터·정렬·페이징이 어떻게 SQL이 되고, 이미지 N+1을 어떻게 피하는가 | [product.md](../domains/catalog/product.md) |
| [장바구니 담기](purchase/seq-cart-add.md) | 동시 담기에서 수량이 사라지지 않고, 첫 담기 경합이 왜 409가 되는가 | [cart.md](../domains/purchase/cart.md) |
| [ERD](images/erd.png) | 테이블과 관계 | `src/main/resources/db/migration` |

## 폴더 구조

장은 **도메인별로** 나누고, 이미지는 한곳에 모은다.

```
diagrams/
├── global/     시스템 전반 — 구성도 · 요청 파이프라인
├── auth/       로그인 · 인증
├── catalog/    상품 · 카테고리
├── purchase/   장바구니 · 주문
├── images/     모든 PNG (장에서 ../images/ 로 참조)
└── src/        손으로 배치를 잡은 원본 (*.mmd, *.html)
```

도메인 폴더 이름은 [domains/](../domains/)와 맞춘다. 새 장을 추가할 때 해당 도메인 폴더가 없으면 만든다.

## 그리는 규칙

- **내용의 기준은 Mermaid**다 — PR diff로 리뷰된다. 각 장의 PNG는 diagram-design 스킬(`import-mermaid`)이
  이 Mermaid를 기본 스킨으로 다시 그린 것이다. 내용을 바꾸면 Mermaid를 먼저 고치고 PNG를 다시 뽑는다
- Mermaid는 장 안의 `<details>` 블록에 둔다. 단, [요청 처리 파이프라인](global/request-pipeline.md)은 읽는 흐름을 끊지 않으려고
  [src/](src/)로 뺐다 — 그 장만 `src/*.mmd`가 내용의 기준이다
- 손으로 배치를 잡은 장은 `src/*.html`을 함께 둔다. PNG를 다시 뽑을 때 레이아웃을 재사용한다
- **PNG는 전부 `images/`에 둔다.** 장에서는 `../images/<이름>.png`로 참조한다
- **실선은 있는 것, 점선은 계획**이다. flowchart는 `-.->`와 점선 테두리, sequence는 `rect` + `Note`로 표시한다
- 이름은 클래스명 그대로 쓴다. 메서드는 시퀀스에서만 적는다
- 그림에 나온 클래스의 공개 메서드·호출 순서·외부 연동을 바꾸는 PR은 해당 그림도 함께 고친다

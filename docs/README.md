# 문서 안내

## 무엇을 하려는가 → 어느 문서

| 하려는 일 | 문서 |
|---|---|
| 지금 어디까지 됐는지, 다음에 뭘 할지 확인 | [roadmap.md](roadmap.md) |
| 패키지 구조·의존 규칙 파악, 새 도메인 추가 | [architecture.md](architecture.md) |
| 특정 도메인의 API·엔티티·규칙 확인 및 변경 | [domains/](domains/) |
| 응답 포맷 확인, 새 컨트롤러 작성 | [conventions/api-response.md](conventions/api-response.md) |
| 새 에러 코드 추가 | [conventions/error-codes.md](conventions/error-codes.md) |
| 주석 작성, "이건 어디에 써야 하나" | [conventions/javadoc.md](conventions/javadoc.md) |
| 브랜치 생성, 커밋 작성, PR 올리기 | [conventions/git.md](conventions/git.md) |
| "왜 이렇게 돼 있지?" 싶을 때 | [decisions/](decisions/) |
| 부하 측정·성능 목표 확인 | [performance/order-stock-concurrency.md](performance/order-stock-concurrency.md) |
| 배포·CI/CD·운영 | [deployment.md](deployment.md) |
| 로컬 실행 환경 세팅 | [../README.md](../README.md) |

## 도메인별

| 도메인 | 문서 |
|---|---|
| user · auth | [domains/auth.md](domains/auth.md) |
| category | [domains/catalog/category.md](domains/catalog/category.md) |
| product | [domains/catalog/product.md](domains/catalog/product.md) |
| 카탈로그 공통 | [domains/catalog/](domains/catalog/README.md) |
| cart | [domains/purchase/cart.md](domains/purchase/cart.md) |
| order | [domains/purchase/order.md](domains/purchase/order.md) |
| payment | [domains/purchase/payment.md](domains/purchase/payment.md) |
| 구매 플로우 공통 · 엔티티 관계 | [domains/purchase/](domains/purchase/README.md) |
| review | [domains/review.md](domains/review.md) |

## 구조 — 세 개의 축

문서를 **변화 속도**에 따라 나눴다. 축이 다른 내용은 같은 파일에 두지 않는다.

| 축 | 무엇 | 어디 | 언제 고치나 |
|---|---|---|---|
| **주제축** | 계약·규약. 코드와 1:1 대응 | `architecture.md`, `domains/`, `conventions/`, `deployment.md`, `performance/` | 코드가 바뀔 때만 |
| **시점축** | 왜 그렇게 정했는가 | `decisions/` | 새 결정이 생길 때만 **추가** |
| **시간축** | 무엇이 끝났고 다음은 뭔가 | `roadmap.md` **한 장** | 단계가 끝날 때마다 |

### 지킬 것

1. **진행 상태는 `roadmap.md`에만 쓴다.** ✅/⬜, 체크박스, 테스트 개수, "구현 완료"류 표기가
   주제축 문서에 들어가면 안 된다. 주제축 문서는 *지금 코드가 이렇다*만 서술한다.
   아직 구현되지 않은 도메인 문서는 맨 위에 `roadmap.md`를 가리키는 한 줄만 둔다.
2. **ADR이 있는 결정의 근거는 ADR에만 쓴다.** 다른 문서는 복사하지 말고 링크한다.
   ADR 기준([decisions/README.md](decisions/README.md))에 못 미치는 결정은 계약 문서에 **한 줄** 근거를 둔다.
   ADR은 한 번 쓰면 고치지 않는다 — 뒤집힐 때 새 번호를 추가한다([decisions/README.md](decisions/README.md)).
3. **링크는 상대경로 마크다운 링크로 쓴다.** `[[wikilink]]`는 쓰지 않는다(어디서도 클릭되지 않는다).

같은 사실이 두 파일에 있으면 하나는 반드시 낡는다. 무언가를 적기 전에 **그 사실의 주인이 누구인지**
먼저 정한다.

### 확인

```bash
# 위키링크 잔존
grep -rn "\[\[" . ../README.md

# 상태 표기가 roadmap 밖에 있는지 (decisions/의 "상태:" 줄과 도메인 문서 상단 1줄은 정상)
grep -rn "구현 완료\|미구현\|부분 구현" . | grep -v roadmap.md

# 아무 사실이나 하나 골라 — 한 파일에서만 나와야 한다
grep -rln "SameSite" .
```

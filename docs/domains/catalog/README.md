# catalog — 카테고리 · 상품

> 이 문서는 **계약**이고, 무엇이 끝났는지는 [roadmap.md](../../roadmap.md)가 소유한다.
> **Category·Product 모두 조회와 ADMIN CRUD까지 코드에 있다.**

ADMIN 접근 제어는 Category 구현과 함께 들어왔다 — `hasRole("ADMIN")`이 `SecurityConfig`에 붙어 있고,
인가 실패는 A002(403)로 나간다
([ADR 0010](../../decisions/platform/0010-access-denied-handler-for-authorization.md)).
Product의 ADMIN CRUD는 같은 배선을 그대로 쓴다.

도메인별 계약은 두 문서가 나눠 소유한다.

| 도메인 | 문서 |
|---|---|
| category | [category.md](category.md) |
| product | [product.md](product.md) |

두 도메인이 맞물리는 지점은 **카테고리 삭제 차단(`CA004`)** 하나다 —
상품이 붙은 카테고리는 지울 수 없고, 그 검사는 `CategoryService`가 `ProductRepository`를 직접 읽어
수행한다([ADR 0014](../../decisions/catalog/0014-category-reads-product-repository.md)).

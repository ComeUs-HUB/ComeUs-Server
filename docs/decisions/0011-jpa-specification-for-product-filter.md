# ADR 0011 — 상품 목록의 동적 필터는 JPA Specification으로 시작한다

- 상태: 채택
- 일자: 2026-09-15
- 관련: [domains/catalog.md](../domains/catalog.md), [conventions/api-response.md](../conventions/api-response.md)

## 맥락

`GET /api/products`는 카테고리·가격 범위·키워드를 **조합**해 거를 수 있어야 하고, 정렬과 페이징도 붙는다.
조건은 앞으로 늘어난다고 봐야 한다(상태, 재고 유무, 할인 여부 등).

선택지는 사실상 둘이다 — Spring Data JPA가 기본 제공하는 **Specification**, 그리고 별도 의존성과
빌드 단계(annotation processor로 Q 클래스 생성)가 필요한 **QueryDSL**이다.

## 결정

**JPA Specification으로 시작한다.** 조건이 복잡해지거나 컴파일 타임 안전성이 실제로 아쉬워지는
시점에 **QueryDSL로 리팩터링**한다.

전환 신호를 미리 적어 둔다 — 아래 중 하나라도 걸리면 재검토한다.

- 조인이 2개 이상 얽힌 조건이 생긴다
- 서브쿼리나 집계(평균 평점 등)가 필터 조건으로 들어온다
- `Specification` 조합 코드가 무슨 쿼리인지 읽어서 알기 어려워진다

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 처음부터 QueryDSL | 의존성과 **빌드 설정(Q 클래스 생성)** 을 먼저 지불해야 한다. 지금 필요한 것은 단순 `AND` 조합뿐이라 그 비용에 상응하는 이득이 없다 |
| 조건 조합마다 쿼리 메서드 추가 | 조건이 n개면 조합이 2ⁿ으로 늘어난다. `findByCategoryAndPriceBetweenAndNameContaining` 류가 쌓이는 순간 손을 못 댄다 |
| `@Query`에 동적 SQL 문자열 조립 | 문자열 연결로 WHERE 절을 만들면 타입 안전성도 정적 검증도 없다 |

## 결과 / 되돌리려면

- Specification은 **런타임에 조립**되므로 오타가 컴파일에서 걸리지 않는다. 필터 조합 테스트로 덮는다
- 리팩터링 범위는 Repository 구현으로 **국한된다.** 서비스는 "조건 객체를 넘기면 페이지가 나온다"만
  알면 되도록, Specification 조립을 서비스에 흩뿌리지 않고 한곳에 모은다 — 이것이 전환 비용을 정하는
  가장 큰 변수다
- 목록 응답은 `PageResponse.of()`를 그대로 쓴다. 이 도메인이 그 첫 실사용처다

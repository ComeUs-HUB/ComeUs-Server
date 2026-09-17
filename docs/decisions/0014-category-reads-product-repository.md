# ADR 0014 — 카테고리 삭제 차단은 `CategoryService`가 `ProductRepository`를 직접 읽는다

- 상태: 채택
- 일자: 2026-09-16
- 관련: [architecture.md](../architecture.md), [domains/catalog.md](../domains/catalog.md)

## 맥락

[architecture.md](../architecture.md)의 의존 규칙은 이렇다.

> **도메인 간 참조는 단방향으로 유지한다.** 현재 유일한 도메인 간 의존은 `auth → user`이며,
> auth는 `UserRepository`가 아니라 `UserService`의 공개 메서드만 경유한다

Product가 생기면 **두 요구가 동시에** 생긴다.

- 상품 목록의 대분류 필터가 자식 소분류 id를 알아야 한다 → `product → category`
- 상품이 붙은 카테고리는 지울 수 없어야 한다(CA004) → `category → product`

즉 **양방향이 되는 것 자체는 피할 수 없다.** 문제는 되돌아오는 쪽을 무엇에 연결하느냐다.
둘 다 Service로 두면 `ProductService ↔ CategoryService` 생성자 주입 순환이 되어 기동이 실패한다.

## 결정

**`CategoryService → ProductRepository`.** 역방향은 `ProductService → CategoryService`로 유지한다.
한 방향만 Service를 경유하고, **되돌아오는 방향은 Repository로 내려서 순환을 끊는다.**

`CategoryService.delete()`는 하위 카테고리 검사(CA002) **다음에**
`ProductRepository.existsByCategoryIdIn()`으로 상품 존재를 검사해 CA004를 던진다.

## 두 Repository는 대칭이 아니다

`CategoryRepository`에 "category 패키지 안에서만 주입한다"는 규칙이 걸려 있으므로, 겉보기에는
이 결정이 그 규칙을 어기는 것처럼 보인다. 그렇지 않다.

그 규칙이 존재하는 이유는 **2depth 불변식이 `Category.child()`에 있어서**다 — Repository를 직접
쓰면 그 불변식을 우회해 3depth를 만들 수 있다. 반면 `ProductRepository.existsByCategoryIdIn()`은
**우회할 규칙이 없는 단순 존재 질의**다. 지킬 것이 없는 질의에 Service 경유를 요구하는 것은
형식만 맞추고 순환을 부르는 선택이다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `CategoryService → ProductService` | **생성자 주입 순환.** Spring이 기동 시점에 실패시킨다. `@Lazy`나 setter 주입으로 깨는 방법은 있으나 순환을 숨길 뿐 설계를 고치지 않는다 |
| 도메인 이벤트 발행 | 삭제 거절은 **동기 응답**이어야 한다. 이벤트로 비동기가 되면 409를 돌려줄 시점을 놓친다 |
| FK `ON DELETE RESTRICT`만으로 처리 | DB가 막아주지만 `DataIntegrityViolationException`이 올라와 **C005(409)**로 나간다. 원인이 "하위 카테고리"인지 "상품"인지 FE가 가릴 수 없다 — `error-codes.md`가 코드 값을 대외 계약으로 정의한 이유와 어긋난다 |
| 양쪽 Javadoc에만 적기 | 배제한 대안이 있으면 본문보다 ADR이 맞다([conventions/git.md](../conventions/git.md)). Javadoc은 "이 코드가 무엇을 하는가"의 주인이지 **"왜 규칙을 깼는가"의 주인이 아니다** |

## 결과 / 되돌리려면

- `architecture.md`의 **"유일한 도메인 간 의존은 `auth → user`"가 거짓이 된다.** 구현과 함께 갱신한다
- 이 예외는 **`category`–`product` 한 쌍에 한정**한다. 새 도메인이 같은 요구를 가지면 그때 다시
  판단한다 — "Repository 직접 주입"을 일반 규칙으로 승격하지 않는다
- 방향을 잘못 잡았는지는 **기동으로 판정된다.** 순환이 생기면 컨텍스트 로딩 자체가 실패하므로
  조용히 넘어갈 수 없다
- 되돌리려면: 삭제 차단을 FK와 C005로 낮추거나(에러 코드 계약이 무뎌진다), 카테고리–상품 관계를
  별도 도메인으로 뽑는다(두 도메인 모두 그 도메인에 의존하게 되어 양방향이 사라진다)

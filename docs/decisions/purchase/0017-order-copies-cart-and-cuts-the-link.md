# ADR 0017 — 주문은 장바구니를 값으로 복사하고 연결을 끊는다

- 상태: 채택
- 일자: 2026-10-02
- 관련: [domains/purchase/](../../domains/purchase/README.md), [architecture.md](../../architecture.md), [ADR 0014](../catalog/0014-category-reads-product-repository.md)

## 맥락

`POST /api/orders`의 입력을 **선택한 `cartItemId` 목록**으로 정했다. 장바구니 전체가 아니라
고른 항목만 주문하므로 장바구니 하나에서 주문이 여러 번 나온다 —
**논리적 cardinality는 `Cart 1 : Order N`이 맞다.**

이를 FK로 매핑할지, 그리고 `OrderService`가 장바구니를 어떻게 읽을지 정해야 했다.

## 결정

**`carts`·`cart_items`와 `orders`·`order_items` 사이에 FK를 두지 않는다.**
`OrderService`가 `cartItemId`로 `CartItem`을 읽어 `OrderItem`으로 **값을 복사**하고 원본을 지운다.
복사가 끝나면 연결은 끊긴다. `cartItemId` 목록은 **요청 입력일 뿐 영속 관계가 아니다.**

cardinality가 1:N인 것은 사실이지만 그 사실은 `User 1 : Order N`이 이미 표현한다 —
Cart는 User와 1:1이므로 둘은 User를 거쳐 이어진다.

**도메인 간 의존은 `order → cart` 단방향이고, `OrderService`가 `CartService`를 경유한다.**
닿는 지점은 `OrderService.create()` 하나이며 메서드는 둘이다 — 선택 항목 조회(소유자 검증 포함)와
주문 성립 후 삭제. 조회·취소는 장바구니를 모른다.

이 ADR은 `order → cart` **하나만** 다룬다. Phase 4가 들여오는 나머지 의존
(`cart → user`·`cart → product`, `order → user`·`order → product`, `payment → order`)은
**엔티티 참조**이고 `product → category` 선례를 따른다 — 목록의 주인은
[architecture.md](../../architecture.md)다.

### ADR 0014와 같은 질문에 다른 답을 낸 이유

[ADR 0014](../catalog/0014-category-reads-product-repository.md)는
`CategoryService → ProductRepository`로 **Repository를 직접 읽었다.** 근거는 순환이었다 —
`ProductService → CategoryService`가 이미 있어 Service를 경유하면 생성자 주입 순환이 된다.

`order → cart`에는 그 순환이 없다. 순환이 없을 때의 선례는 `auth → user`이며 Service를 경유한다.
게다가 주문은 장바구니에서 **읽고 지우는** 둘을 하므로, 빈 장바구니 처리 같은 규칙이
`CartService`에 모여 있어야 `OrderService`로 새지 않는다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `order_items`가 `cart_item_id` FK를 든다 | 주문이 성립하면 그 `cartItem`을 지우고 `DELETE /api/cart`로 전체를 비울 수도 있다. 가리킬 행이 사라져 dangling FK가 된다 |
| `orders`가 `cart_id`를 든다 | 장바구니는 지금 바뀌는 상태이고 주문은 `priceAtOrder`로 고정한 기록이다. 고정된 기록이 살아있는 행을 참조하면 **스냅샷의 의미가 없어진다** |
| `carts`가 `order_id`를 든다 | 장바구니가 주문을 알게 되어 양방향이 된다. [architecture.md](../../architecture.md)의 단방향 규칙 위반이다 |
| 입력을 장바구니 전체로 받는다 | 일부만 주문하는 흐름을 막는다. 또 주문마다 장바구니를 비우게 되어 남겨둘 항목을 표현할 수 없다 |
| `OrderService`가 `CartItemRepository`를 직접 읽는다 | "타인 항목은 404로 숨긴다"는 cart 쪽 판단이 order로 샌다. 순환이 없어 Repository로 내릴 이유도 없다 |

## 결과 / 되돌리려면

- **주문 조회는 장바구니를 모른다.** `OrderItem`이 주문 시점 값을 모두 들고 있어서
  (상품ID·수량·`priceAtOrder`) 조회 경로에 cart 의존이 생기지 않는다
- **취소는 항목을 장바구니로 되돌리지 않는다.** 되돌리면 그 사이 담은 내용과 충돌해
  주문 취소가 장바구니를 덮어쓴다
- `CartService → OrderService` 역방향 호출이 생기면 순환이 되고
  [ADR 0014](../catalog/0014-category-reads-product-repository.md)와 같은 문제에 부딪힌다.
  "주문한 상품 다시 담기"는 클라이언트가 `POST /api/cart/items`를 부르면 되므로 그 요구가 아니다
- 의존 목록의 주인은 [architecture.md](../../architecture.md)다. 이 ADR은 근거만 소유한다
- `OrderServiceTest`에서 `CartService` 모킹이 필요한 것은 **주문 생성 케이스뿐**이다
- ⚠️ `Order`가 `User`를 참조하므로 생성 시 `User` 인스턴스가 필요하다. 현재 `UserService`의 공개
  메서드는 모두 DTO를 반환하므로, 착수 시 `CategoryService.getCategory()`에 대응하는
  **엔티티 반환 메서드를 `UserService`에 더해야 한다.** 장바구니 조회는 `UserPrincipal`의 `userId`만
  쓰므로 이 의존과 무관하다

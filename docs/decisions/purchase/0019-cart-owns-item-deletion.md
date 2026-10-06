# ADR 0019 — 장바구니 항목 삭제는 애그리거트가 소유한다

- 상태: 채택
- 일자: 2026-10-04
- 관련: [domains/purchase/cart.md](../../domains/purchase/cart.md), [ADR 0017](0017-order-copies-cart-and-cuts-the-link.md)

## 맥락

`Cart`가 합산과 재고 검사를 소유하기로 한 뒤, 항목 **삭제**를 엔티티가 할지 Repository가 할지
정해야 했다. 삭제 경로는 셋이다 — 항목 하나 삭제, 전체 비우기, 그리고 주문 성립 후 선택 항목
삭제([ADR 0017](0017-order-copies-cart-and-cuts-the-link.md)).

처음 가정은 **"컬렉션 수준이 아니라 DB 수준에서 지우면 되므로 `deleteById`로 충분하다"**였다.
전제가 틀렸다. `CascadeType.REMOVE`는 **부모가 삭제될 때만** 전파되므로 `cascade = ALL`만으로는
컬렉션에서 자식을 빼도 `DELETE`가 나가지 않는다 — `cart_id`가 그대로인 행이 남아 다음 조회에서
항목이 되살아난다.

| 상황 | `cascade = REMOVE` | `orphanRemoval = true` |
|---|---|---|
| 부모가 삭제됨 | 자식 `DELETE` | 자식 `DELETE` |
| **컬렉션에서 자식만 제거** | 아무 일 없음 | 자식 `DELETE` |

즉 선택지는 "지우는 두 방법"이 아니라 **`Cart.removeItem()`이 존재할 수 있는지**였다.

## 결정

**`orphanRemoval = true`로 삭제 책임을 애그리거트 안에 둔다.**

- `removeItem`·`clear`가 컬렉션 조작만으로 끝난다
- `CT001` 판정("이 장바구니의 항목인가")이 `getItem` 하나에 모인다
- 주문 트랜잭션의 삭제도 영속성 컨텍스트 안에서만 일어나 flush 순서에 의존하지 않는다

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `CartItemRepository.deleteById` | `CT001` 판정이 서비스로 간다. 계약이 **타인 항목도 404로 숨기라**고 정했으므로 그 판정은 삭제 경로와 수량 변경 경로 양쪽에 생긴다 — 같은 불변식의 소유자가 둘이 되고, 하나는 반드시 낡는다 |
| 전체 비우기만 벌크 `delete` | 영속성 컨텍스트를 **우회한다.** 자동 flush는 query space가 겹칠 때로 제한되어 담기(INSERT) 직후 비우기 순서를 매번 따져야 하고, 1차 캐시에 남은 엔티티는 삭제를 모른다 — 이후 수정이 **0 row `UPDATE`로 조용히 사라진다.** 주문 트랜잭션이 재고 차감(수정)과 항목 삭제를 함께 하므로([ADR 0017](0017-order-copies-cart-and-cuts-the-link.md)) 가정이 아니라 실제 시나리오다 |
| 건별 `deleteAll` + 컬렉션 유지 | 컨텍스트는 알지만 **컬렉션이 모른다.** `cascade = ALL`에 `PERSIST`가 포함되어 flush 때 삭제 표시된 엔티티에 persist가 전파되면 `deleted entity passed to persist`로 터진다. "컬렉션에서도 빼야 한다"는 요구가 사라지지 않고, 그것을 자동화한 것이 `orphanRemoval`이다 |

## 전제 — [ADR 0017](0017-order-copies-cart-and-cuts-the-link.md)이 가능하게 했다

`orphanRemoval`은 자식이 **독점 소유**일 때만 성립한다. `order_items`가 `cart_item_id` FK를
들었다면(0017이 배제한 대안) 주문 성립 후 `cartItem` 삭제가 FK 제약 위반이 되고, 독점 소유가
깨져 이 선택은 불가능했다.

## 결과 / 되돌리려면

- **비용은 `DELETE` N건이다**(벌크는 1건). 항목 종류 상한 50으로 N을 유계로 만들었다 —
  상한의 나머지 근거는 [cart.md](../../domains/purchase/cart.md)가 소유한다
- 컬렉션 선행 로드가 필요하지만, 소유자 검증 때문에 어차피 `Cart`를 로드한다. 추가 비용이 아니다
- **`CartItemRepository`가 필요 없을 수 있다** — 모든 쓰기가 `Cart`를 거치고 조회는
  `CartRepository.findByUserId` 하나로 끝난다. 2단계에서 판정한다
- 반대 사례는 같은 저장소 안에 있다. `RefreshToken`은 `User`가 컬렉션으로 들고 있지 않고 목적이
  무효화라 **벌크 delete가 맞다**(`deleteAllByUserId`). 조건이 다르면 결론도 다르다
- **자식 건수가 수백 이상인 것이 정상인 테이블이면 반대로 가야 한다.** 판정 기준은 넷이다 —
  독점 소유인가, 불변식 때문에 컬렉션을 이미 로드하는가, 한 트랜잭션에 추가·수정·삭제가 섞이는가,
  건수 규모가 어떤가

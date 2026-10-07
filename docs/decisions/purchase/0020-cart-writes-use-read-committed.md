# ADR 0020 — 장바구니 쓰기는 READ COMMITTED에서 루트 락을 잡는다

- 상태: 채택
- 일자: 2026-10-07
- 관련: [domains/purchase/cart.md](../../domains/purchase/cart.md), [ADR 0019](0019-cart-owns-item-deletion.md), PR #13 리뷰 1번

## 맥락

### 현재 구현

같은 회원의 동시 쓰기(더블 클릭·여러 탭·재시도)를 직렬화하려고 쓰기 경로가 `carts` 행을 잠근다.

```
getVisibleProduct   일반 SELECT          담기만. P001이면 장바구니를 건드리지 않는다
existsByUserId      일반 SELECT          없는 행을 FOR UPDATE하면 gap lock끼리 교착한다
lockByUserId        SELECT … FOR UPDATE  carts 행 하나만 잠근다
findByUserId        일반 SELECT          @EntityGraph로 항목·상품을 함께 읽는다
```

- 락을 `carts` 행에만 거는 이유: 그래프 조회에 락을 함께 걸면 조인된 `products` 행까지 잠긴다
- 격리 수준은 따로 설정하지 않았다 — MySQL InnoDB 기본값 **REPEATABLE READ**

### 문제 — 락 뒤의 일반 SELECT가 락 이전 스냅샷을 읽는다

REPEATABLE READ에서 두 조회는 다른 것을 본다.

| 조회 | 읽는 것 |
|---|---|
| 일반 `SELECT`(일관 읽기) | 트랜잭션의 **첫 일반 SELECT 때 만든 스냅샷**. 이후 일반 SELECT는 모두 이것을 본다 |
| `SELECT … FOR UPDATE`(현재 읽기) | 지금 커밋된 최신 값 |

쓰기 경로는 락보다 **먼저** 일반 SELECT를 하므로, 락을 기다리는 동안 커밋된 변경이 락 뒤의 `findByUserId`에 보이지 않는다.
락은 순서만 직렬화하고, 읽는 데이터는 기다리기 전 그대로다.

**시나리오 ① — 이미 담긴 상품(수량 2)을 동시에 담는다 → Lost Update**

```
A                                       B
getVisibleProduct ─ 스냅샷 생성
                                        getVisibleProduct ─ 스냅샷 생성 (항목 수량 2)
lockByUserId ─ 획득
findByUserId ─ 2
2 + 1 = 3, UPDATE, 커밋 ─ 락 해제
                                        lockByUserId ─ A의 커밋까지 대기 후 획득
                                        findByUserId ─ 스냅샷이라 여전히 2
                                        2 + 1 = 3, UPDATE, 커밋
기대 4, 실제 3
```

같은 행을 두 번 UPDATE하므로 uk는 관여하지 않는다. 재고 검사(`CT002`)도 낡은 수량으로 한다.

**시나리오 ② — 없는 상품을 동시에 담는다 → uk 위반**

B의 스냅샷에는 A가 INSERT한 항목 행이 없다. B도 INSERT해 `uk_cart_items_cart_id_product_id` 위반(`C005`)으로 끝난다.

**테스트가 잡지 못한 이유** — `@DataJpaTest`의 H2는 기본 격리 수준이 READ COMMITTED라 재현되지 않고,
`CartServiceTest`는 Mockito라 호출 순서만 본다. 순서는 맞았다.

## 결정

**장바구니 쓰기 메서드는 `@Transactional(isolation = READ_COMMITTED)`로 실행한다. 루트 락은 그대로 둔다.**

READ COMMITTED에서는 일반 SELECT도 **문장마다** 최신 커밋을 본다. 락이 순서를 직렬화하고,
READ COMMITTED가 "락을 얻은 뒤의 읽기는 최신"을 보장한다 — **둘이 맞물려야** 하고 하나만으로는 안 된다.
READ COMMITTED만으로는 Lost Update를 막지 못한다.

```
B: lockByUserId ─ A의 커밋까지 대기 후 획득
   findByUserId ─ 새 문장이라 최신 커밋 = 3
   3 + 1 = 4
```

함께 얻는 것: READ COMMITTED는 검색에 **gap lock을 쓰지 않는다.** 없는 행을 잠글 때의 교착 위험도 사라진다.
그래도 `existsByUserId`로 확인한 뒤 잠그는 순서는 유지한다 — 격리 수준에 기대지 않는 쪽이 안전하다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 그래프 조회에도 `@Lock` | 현재 읽기가 되지만 조인된 `products` 행까지 잠겨 다른 회원의 담기와 주문의 재고 차감이 기다린다 |
| 락 앞의 일반 SELECT를 없앤다 | `existsByUserId`(gap lock 회피)와 `getVisibleProduct`(P001이면 행을 남기지 않음)가 락보다 앞서야 한다 |
| 전역 격리 수준을 READ COMMITTED로 | 장바구니 밖 트랜잭션의 동작까지 바뀐다. 필요 이상으로 범위가 크다 |
| 낙관적 락(`@Version`) | 스키마 변경(V5)이 필요하고 충돌 시 사용자에게 재시도를 요구한다. 항목만 바뀌면 루트 버전이 오르지 않아 강제 증가도 필요하다 |
| 락 획득 후 `refresh` | REPEATABLE READ에서는 `refresh`도 일반 SELECT라 같은 스냅샷을 읽는다 |

## 결과 / 되돌리려면

- **새 장바구니 쓰기 메서드에도 같은 격리 수준을 붙여야 한다.** 빠뜨리면 이 문제가 다시 생긴다 —
  `CartServiceTest`가 쓰기 메서드 전체의 격리 수준을 검사해 고정한다
- 비용은 트랜잭션마다 커넥션 격리 수준을 바꿨다 되돌리는 것이다(`HibernateJpaDialect`가 지원한다)
- **H2로는 검증되지 않는다.** Cart 3단계 MySQL 실기동에서 같은 회원의 동시 담기 2건이 **합산 4**로 끝나는지,
  동시 첫 담기가 교착이 아니라 `C005`로 끝나는지 본다
- Order 착수 전 동시성 ADR은 주문 트랜잭션이 장바구니 루트 락을 잡을 때 **같은 격리 수준을 쓸지** 함께 정한다
- 되돌리려면 격리 수준 지정을 빼고, 락 이후 읽기를 모두 현재 읽기로 바꾸는 다른 방법을 먼저 마련해야 한다

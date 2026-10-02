# ADR 0018 — 상품 옵션(사이즈·색상)을 초기 범위 밖에 둔다

- 상태: 채택
- 일자: 2026-10-02
- 관련: [domains/purchase.md](../../domains/purchase.md), [domains/catalog.md](../../domains/catalog.md), [ADR 0015](../catalog/0015-no-stock-quantity-in-public-response.md)

## 맥락

계약은 `CartItem`을 `{ product, size, color, quantity }`로 두고
**"동일 항목은 상품ID + 옵션 조합으로 판단한다"**고 적고 있었다.
그런데 `Product`와 `V3__create_products.sql`에는 `size`·`color`가 없고 재고는 `stock_quantity` 하나다.

- **옵션별 재고를 표현할 수 없다** — "M은 10개, L은 품절"이 불가능하다
- **검증할 원천이 없다** — `"XXXXL"`을 담아도 거절할 근거가 없고, 그 값이 주문 스냅샷까지 흘러간다
- `CT002` 검사 기준이 애매해진다 — 같은 상품의 M 5개 + L 5개를 재고 10개와 항목별로 볼지 합계로 볼지

옵션은 `CartItem`의 **식별 키**(동일 항목 판정)라서 정하지 않으면 테이블을 만들 수 없다.

## 결정

**옵션을 뺀다.** `CartItem`·`OrderItem`은 `{ product, quantity }`이고 동일 항목 판정은 상품ID 하나다.
`CT002` 검사는 "기존 수량 + 요청 수량 vs `stock_quantity`" 비교 한 번으로 끝난다.

이것은 **옵션이 불필요하다는 결정이 아니다.** 재고 모델을 바꾸는 비용을 Phase 4에서
지불하지 않는다는 결정이다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `ProductOption` 테이블을 추가하고 재고를 옵션 단위로 내린다 | 커머스의 올바른 모델이지만 Phase 3를 크게 건드린다 — `Product.stockQuantity`·`decreaseStock`·`applyStockStatus`·`SOLD_OUT` 판정·`V3` 스키마·`ProductServiceTest`가 모두 바뀐다. 무엇보다 [ADR 0015](../catalog/0015-no-stock-quantity-in-public-response.md)의 전제(`stockQuantity == 0` ↔ `status == SOLD_OUT`)가 깨져 **0015를 대체하는 ADR이 필요하다.** 재고 모델을 바꿀 실제 요구가 생겼을 때 지불할 비용이다 |
| 옵션을 enum으로 고정(`S/M/L/XL`)하고 재고는 상품 단위로 유지한다 | 검증은 되고 Phase 3도 바뀌지 않는다. 그러나 옵션별 재고가 없는 채로 옵션을 노출하면 **"M 품절"을 표현할 수 없다** — 사용자는 담을 수 있지만 주문에서 거절된다. 반쪽 기능을 계약에 넣지 않는다 |
| 옵션을 자유 문자열로 받고 재고는 상품 단위로만 검사한다 | 재고 정합성은 유지되지만 검증되지 않은 값이 `order_items`의 스냅샷으로 **영구히 남는다.** 나중에 enum으로 좁히면 기존 주문 데이터를 해석할 수 없다 |

## 결과 / 되돌리려면

- 도입 조건은 [roadmap.md](../../roadmap.md) 미결 사항에 있다. 재고를 옵션 단위로 내릴지 함께 정해야 하고,
  그때 **[ADR 0015](../catalog/0015-no-stock-quantity-in-public-response.md)를 대체하는 ADR이 필요하다**
- **되돌리는 비용은 시점에 달렸다.** 주문 데이터가 쌓인 뒤 옵션을 넣으면 기존 `order_items`를
  어떻게 해석할지 정해야 한다. Cart·Order 코드가 0줄인 지금이 가장 싸다
- `ProductListResponse`·`ProductDetailResponse`는 바뀌지 않는다 — 옵션 필드가 애초에 없었다

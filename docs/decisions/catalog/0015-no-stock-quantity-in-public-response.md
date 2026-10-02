# ADR 0015 — 공개 상품 응답은 재고 수량을 담지 않는다

- 상태: 채택
- 일자: 2026-09-22
- 관련: [domains/catalog.md](../../domains/catalog.md), [ADR 0013](0013-hide-soft-deleted-products-everywhere.md)

## 맥락

Phase 3 8단계에서 `GET /api/products`와 `/api/products/{id}`를 `permitAll`로 열었다.
비회원 탐색이 구매 전환의 입구라 여는 것 자체는 선택지가 아니었다.

여는 순간 **응답에 담긴 것이 전부 공개 데이터가 된다.** `ProductDetailResponse`는
`stockQuantity`를 담고 있었다. 상세를 주기적으로 폴링하면 재고 감소분으로 **판매량이 추정된다** —
경쟁사에 넘기고 싶지 않은 숫자다. 저장소에 rate limit이 없어(Phase 6~7에서 다룬다) 폴링 비용도 낮다.

동시에 구매 버튼을 잠그려면 클라이언트가 "살 수 있는가"는 알아야 한다. 정할 것은
**품절을 무엇으로 표현하는가**였다.

## 결정

**공개 응답(`ProductListResponse`·`ProductDetailResponse`)에서 `stockQuantity`를 뺀다.**
품절은 두 DTO가 이미 담고 있는 `status`가 표현한다.

이것이 성립하는 이유는 엔티티가 동기화를 보장하기 때문이다. `create`·`decreaseStock`·
`increaseStock`·`updateInfo`가 전부 `applyStockStatus()`를 거치므로 `stockQuantity == 0`과
`status == SOLD_OUT`은 **항상 같은 값**이다. 게다가 `HIDDEN`은 공개 조회에서 빠지므로
([ADR 0013](0013-hide-soft-deleted-products-everywhere.md)) 공개 응답의 `status`는
`ON_SALE`/`SOLD_OUT` 둘뿐이다 — 클라이언트 입장에서 이 필드는 정확히 "살 수 있나/없나"다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 그대로 노출한다 | 판매량 추정 외에도, 재고 수량은 **조회 시점 값이라 신뢰할 수 없다.** 최종 판정은 주문 시점 `decreaseStock`의 `P002`(409)다. 항상 낡는 숫자를 공개해 얻는 것이 없다 |
| 상한을 씌운 파생 필드 (`min(재고, 10)`) | 수량 셀렉터 상한을 주면서 큰 재고는 가린다. 그러나 경쟁사가 알고 싶은 구간이 **10 미만**이라 그 구간에서는 정확한 값이 그대로 나가 방어가 얕다. 수량 상한은 Phase 4 장바구니의 문제지 상세 조회의 문제가 아니다 |
| 구간화 (`stockLevel: SUFFICIENT/LOW/SOLD_OUT`) | "3개 남음" 같은 희소성 표시가 가능해진다. 다만 **임계값이 곧 정책**이고(몇 개부터 `LOW`인가) 마케팅 요구가 실제로 생기기 전에 정할 이유가 없다. `status`로 시작해 필요해지면 필드를 더하는 쪽이 싸다 |
| 권한으로 분기한다 (ADMIN에게만 재고) | 같은 URL이 권한에 따라 다른 바디를 준다. [ADR 0013](0013-hide-soft-deleted-products-everywhere.md)이 같은 이유로 배제했다 — 권한 판정이 필터체인에서 끝나지 않고 서비스·DTO로 샌다 |

## 결과 / 되돌리려면

- **되돌리는 비용이 낮고, 미루는 비용이 높다.** 필드를 다시 넣는 것은 추가라 클라이언트를 깨지
  않는다. 반대로 한 번 공개한 필드를 빼는 것은 깨는 변경이다 — 클라이언트가 0개인 지금이 가장 싸다
- `ProductDetailResponse`는 8단계의 `POST`·`PUT` 응답도 공유한다. 그래서 **ADMIN도 쓰기 직후
  재고를 돌려받지 못한다.** 관리 화면을 만들 때 `ProductAdminResponse`를 분리하는 것이 제자리이며,
  공개 계약을 깨지 않는 확장이다 — [ADR 0013](0013-hide-soft-deleted-products-everywhere.md)이
  관리자 엔드포인트를 미룬 것과 같은 판단이다
- ⚠️ **FE가 수량 셀렉터 상한을 재고에서 얻을 수 없다.** Phase 4에서 장바구니 담기가 `P002`(409)로
  거절되는 것을 UX가 감당해야 한다. 상한이 정말 필요하면 그때 **장바구니 응답**에 둔다 —
  상품 조회에 되돌려 놓는 것이 아니다

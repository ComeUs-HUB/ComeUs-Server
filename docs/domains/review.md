# review — 리뷰

> 아직 코드가 없다. 이 문서는 **구현할 계약**이고, 진행 현황은 [roadmap.md](../roadmap.md)가 소유한다.
> `ReviewErrorCode`만 선반영돼 있다.

배송 완료된 주문에 한해 리뷰를 남긴다. [purchase.md](purchase.md)의 Order 상태 흐름이 먼저
확정되어야 작성 가능 조건을 판단할 수 있다.

## 엔티티

id, user, product, order, rating(1~5), content, imageUrls, createdAt

## API

| Method | URI | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/products/{id}/reviews` | 상품 리뷰 목록 | 비회원 |
| POST | `/api/products/{id}/reviews` | 리뷰 작성 | 회원 |
| PUT | `/api/reviews/{id}` | 리뷰 수정 | 회원(본인) |
| DELETE | `/api/reviews/{id}` | 리뷰 삭제 | 회원(본인) / ADMIN |

## 규칙

- **DELIVERED 상태 주문에 한해** 작성 가능 → 아니면 `R003 REVIEW_NOT_ALLOWED`
- 중복 작성 불가 → `R002 REVIEW_ALREADY_EXISTS`
- 수정·삭제는 작성자 본인만 (ADMIN은 삭제 가능). 본인 확인은 **서비스 계층**에서 한다
- 평균 평점은 상품 조회 시 함께 반환한다

## 착수 전 정할 것

- 중복 방지 단위 — **주문 단위인지 주문 내 개별 상품 단위인지.** 한 주문에 여러 상품이 있을 수 있다
- 평균 평점 집계 방식 — 매 조회 시 실시간 집계 vs 상품 엔티티에 캐시된 값 유지 (성능 관점)

이미지 저장 방식은 **정해졌다** — `review_images` 별도 테이블에 URL만 담고, 스토리지는 로컬로
시작해 추후 AWS S3로 옮긴다. 상품 이미지와 같은 방식이다
([ADR 0012](../decisions/0012-image-urls-in-separate-table.md)).

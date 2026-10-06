# category — 카테고리

> 이 문서는 **계약**이고, 무엇이 끝났는지는 [roadmap.md](../../roadmap.md)가 소유한다.
> 조회와 ADMIN CRUD까지 코드에 있다.

상품은 [product.md](product.md)가, 공통 배선은 [README.md](README.md)가 소유한다.
## 엔티티

id, name, parentCategory(self-join, nullable), displayOrder, createdAt

## API

| Method | URI | 설명 | 권한 | 성공 응답 |
|---|---|---|---|---|
| GET | `/api/categories` | 전체 카테고리 트리 조회 | **비회원** | 200 |
| POST | `/api/categories` | 카테고리 생성 | ADMIN | **201** + `Location` |
| PUT | `/api/categories/{id}` | 카테고리 수정 | ADMIN | 200 |
| DELETE | `/api/categories/{id}` | 카테고리 삭제 | ADMIN | 200 + `data: null` |

- 단건 조회 API는 **없다.** 트리 하나로 화면이 구성되므로 만들지 않았다
- `Location`은 절대 URL이 아니라 **상대 경로**(`/api/categories/1`)다.
  운영에서는 `nginx(https://api.example.com) → 앱(http://localhost:8080)` 구조라
  앱이 보는 주소가 내부 주소다 — 절대 URL을 만들면 그 값이 그대로 클라이언트에 나가 따라갈 수 없다.
  상대 경로면 받는 쪽이 자기가 요청한 주소를 기준으로 해석하므로(RFC 3986 §5) 프록시 구성과 무관해진다
  ([deployment.md](../../deployment.md))
- DELETE가 204가 아닌 이유: 바디가 없으면 공통 `{success, data, error}` 포맷이 깨진다
  ([conventions/api-response.md](../../conventions/api-response.md))

## 규칙

- **2depth 구조**(대분류 > 소분류)를 self-join으로 표현한다
- 하위 카테고리가 있으면 삭제할 수 없다 → `CA002 CATEGORY_HAS_CHILDREN`
- 전시 순서는 `displayOrder` 필드로 관리한다. 정렬은 `displayOrder` 오름차순, **동률이면 `id`**
- 삭제는 **하드 삭제**다. 상품(`HIDDEN` soft delete)과 다르다 — 카테고리는 주문 이력에
  매달리지 않아 지워도 과거 기록이 깨지지 않는다
- 수정은 **이름과 전시 순서만** 바꾼다. 부모 변경은 요청 DTO에 필드조차 없다
- 상품이 붙어 있으면 삭제할 수 없다 → `CA004 CATEGORY_HAS_PRODUCTS` ([product.md](product.md) 참고)

## 확정된 것 (착수 시 결정)

- **3단계 이상 계층은 두지 않는다.** self-join을 유지하되 깊이 제한은 **DB가 아니라
  `Category.child()` 정적 팩터리가 강제**한다 — self-join 자체는 3depth 이상도 구조적으로
  허용하기 때문이다. 위반은 `CA003 CATEGORY_DEPTH_EXCEEDED`(400)
- **트리 응답은 대분류 배열에 소분류를 중첩**한다. 소분류의 `children`은 **항상 빈 배열**이고,
  이 점이 2depth 계약을 응답 자체로 드러낸다. 조회는 **쿼리 1회** — 전부 읽어 메모리에서 묶는다


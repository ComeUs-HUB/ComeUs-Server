# 상품 목록 조회

- **답하는 질문**: 비회원 목록 조회가 필터·정렬·페이징을 어떻게 SQL로 바꾸고, 이미지 N+1을 어떻게 피하는가
- **범위**: `GET /api/products` 한 건. 상세 조회·쓰기는 제외
- **원천**: [product.md](../../domains/catalog/product.md) · ADR [0011](../../decisions/catalog/0011-jpa-specification-for-product-filter.md) · [0012](../../decisions/catalog/0012-image-urls-in-separate-table.md) · [0013](../../decisions/catalog/0013-hide-soft-deleted-products-everywhere.md) · [0015](../../decisions/catalog/0015-no-stock-quantity-in-public-response.md)

![상품 목록 조회 시퀀스](../images/seq-product-list.png)

<details>
<summary>Mermaid 원본 — 내용은 이 블록이 기준이고 PNG는 여기서 다시 그린다</summary>

```mermaid
sequenceDiagram
    autonumber
    actor C as 비회원
    participant PC as ProductController
    participant PS as ProductService
    participant CS as CategoryService
    participant Spec as ProductSpecifications
    participant PR as ProductRepository
    participant DB as MySQL

    C->>PC: GET /api/products?categoryId&minPrice&maxPrice&keyword&sort&size
    Note over PC: 인증 불필요 (permitAll)
    PC->>PC: sanitize(pageable)
    alt 정렬 필드가 createdAt · price · name 밖
        PC-->>C: 400 C001
    end
    Note over PC: size는 100에서 잘린다

    PC->>PS: getList(request, pageable)
    opt categoryId가 있으면
        PS->>CS: collectSelfAndChildIds(categoryId)
        CS->>DB: 자기 + 소분류 id
        alt 결과가 비면
            CS-->>C: 404 CA001
        end
    end
    PS->>Spec: search(condition)
    Note over Spec: status <> HIDDEN은 항상 붙는다<br/>나머지 조건은 값이 있을 때만
    PS->>PR: findAll(spec, pageable)
    PR->>DB: ① select products ... limit (+ count)
    PS->>PS: ProductListResponse.from — 대표 이미지 접근
    PR->>DB: ② select product_images where product_id in (...)
    Note over PR,DB: @BatchSize(100)이 이미지 조회를 1건으로 묶는다<br/>없으면 상품 수만큼 쿼리가 나간다
    PS-->>PC: PageResponse
    PC-->>C: 200 ApiResponse(PageResponse) — stockQuantity 없음
```

</details>

## 읽는 법

- 대분류를 고르면 소분류 상품까지 나온다. 카테고리가 2depth로 고정이라 재귀 없이 쿼리 한 번으로 id를 모은다
- 카테고리가 없으면 빈 목록이 아니라 404다 — 자기 id가 항상 결과에 들어가므로 빈 결과가 곧 "없는 카테고리"다
- `HIDDEN`(soft delete)은 목록·상세·장바구니 어디서나 보이지 않는다(ADR 0013)
- `@BatchSize`를 페이지 상한과 같은 100으로 맞춰서, 어느 페이지든 이미지 쿼리가 1건이다
- 2026-09-26 MySQL 실기동에서 상품 5건 조회에 쿼리 2건이 나가는 것을 확인했다(첫 페이지가 다 차지 않으면 Spring Data가 count 쿼리를 생략한다)

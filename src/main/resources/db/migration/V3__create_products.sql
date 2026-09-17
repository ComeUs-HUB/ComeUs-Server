-- Phase 3 · 5단계 — 상품 스키마
--
-- 작성 기준: Product·ProductImage 엔티티가 생성하는 DDL과 1:1로 맞춘다.
-- ddl-auto: validate 가 이 스키마와 엔티티 매핑을 대조하므로 컬럼명·타입·길이가 어긋나면 기동이 실패한다.
-- ⚠️ 테스트는 H2 create-drop(엔티티에서 스키마 생성)이라 이 파일을 실행하지 않는다.
--    즉 여기가 엔티티와 어긋나도 ./gradlew test 는 통과한다 — MySQL 기동으로만 확인된다.
--
-- 이 파일은 한 번 적용된 뒤로 수정하지 않는다. 이후 변경은 V4__, V5__ … 로 추가한다.

CREATE TABLE products
(
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100)   NOT NULL,
    -- 엔티티의 @Column(columnDefinition = "TEXT")와 짝이다.
    -- 아무것도 붙이지 않으면 Hibernate가 VARCHAR(255)로 잡고, @Lob은 LONGTEXT가 되어 둘 다 어긋난다.
    description    TEXT           NULL,
    -- 금액은 부동소수점을 쓰지 않는다. Phase 4가 priceAtOrder × quantity 를 합산한다.
    price          DECIMAL(10, 2) NOT NULL,
    stock_quantity INT            NOT NULL,
    category_id    BIGINT         NOT NULL,
    -- enum은 네이티브 ENUM이 아니라 VARCHAR로 둔다. 엔티티의 @JdbcTypeCode(SqlTypes.VARCHAR)와 짝이다.
    -- ON_SALE / SOLD_OUT / HIDDEN. HIDDEN이 soft delete이며 행은 남는다.
    status         VARCHAR(20)    NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- InnoDB가 category_id 인덱스를 자동으로 만드므로 별도 INDEX를 두지 않는다(V2에서 확인된 전제).
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product_images
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    product_id    BIGINT       NOT NULL,
    -- 파일 바이트가 아니라 URL만 담는다(ADR 0012). 로컬 → S3 전환에 스키마 변경이 없도록
    -- 버킷·리전·키가 붙은 길이를 미리 감안해 500자로 둔다.
    image_url     VARCHAR(500) NOT NULL,
    -- 전시 순서. Product가 0부터 차례로 부여한다.
    display_order INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

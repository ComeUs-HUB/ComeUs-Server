-- Phase 4 · 1단계 — 장바구니 스키마
--
-- 작성 기준: Cart·CartItem 엔티티가 생성하는 DDL과 1:1로 맞춘다.
-- ddl-auto: validate 가 이 스키마와 엔티티 매핑을 대조하므로 컬럼명·타입·길이가 어긋나면 기동이 실패한다.
-- ⚠️ 테스트는 H2 create-drop(엔티티에서 스키마 생성)이라 이 파일을 실행하지 않는다.
--    즉 여기가 엔티티와 어긋나도 ./gradlew test 는 통과한다 — MySQL 기동으로만 확인된다.
--
-- 이 파일은 한 번 적용된 뒤로 수정하지 않는다. 이후 변경은 V5__, V6__ … 로 추가한다.

CREATE TABLE carts
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    -- 회원 1명당 장바구니 1개. 가입이 아니라 첫 담기에서 행이 생긴다.
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_carts_user_id UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cart_items
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    -- 담기 검사(CT002)는 합산 후 수량으로 하며 재고를 점유하지 않는다(ADR 0016).
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 동일 항목은 상품ID로 판단한다(ADR 0018 — 옵션은 범위 밖).
    -- 합산은 Cart.addItem 이 하고, 이 제약은 그것을 우회한 경로를 막는 최후 방어선이다.
    CONSTRAINT uk_cart_items_cart_id_product_id UNIQUE (cart_id, product_id),
    -- InnoDB가 FK 컬럼 인덱스를 자동으로 만들므로 별도 INDEX를 두지 않는다(V2에서 확인된 전제).
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

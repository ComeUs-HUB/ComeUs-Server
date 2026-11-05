-- Phase 4 · 4단계 — 주문 스키마
--
-- 작성 기준: Order·OrderItem 엔티티가 생성하는 DDL과 1:1로 맞춘다.
-- ddl-auto: validate 가 이 스키마와 엔티티 매핑을 대조하므로 컬럼명·타입·길이가 어긋나면 기동이 실패한다.
-- ⚠️ 테스트는 H2 create-drop(엔티티에서 스키마 생성)이라 이 파일을 실행하지 않는다.
--    즉 여기가 엔티티와 어긋나도 ./gradlew test 는 통과한다 — MySQL 기동으로만 확인된다.
--
-- 이 파일은 한 번 적용된 뒤로 수정하지 않는다. 이후 변경은 V6__, V7__ … 로 추가한다.

CREATE TABLE orders
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    user_id         BIGINT         NOT NULL,
    -- 서버가 발급하고 토스 orderId로 쓴다. 토스 제약이 6~64자다.
    order_number    VARCHAR(64)    NOT NULL,
    -- NULL을 허용하면 MySQL uk가 NULL 중복을 허용해 보호가 조용히 사라진다(ADR 0024).
    idempotency_key VARCHAR(64)    NOT NULL,
    -- 승인을 선점한 시도의 키. 선점 전과 거절 뒤에는 NULL이다(ADR 0026).
    payment_key     VARCHAR(200)   NULL,
    status          VARCHAR(20)    NOT NULL,
    -- products.price(10,2) × 수량을 항목 수만큼 합산하므로 자릿수를 넓힌다.
    total_amount    DECIMAL(14, 2) NOT NULL,
    recipient_name  VARCHAR(50)    NOT NULL,
    recipient_phone VARCHAR(20)    NOT NULL,
    address         VARCHAR(255)   NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    -- user_id 범위로 묶어 타인의 키 선점을 막는다. 앞 컬럼이 FK 인덱스를 겸한다(ADR 0024).
    CONSTRAINT uk_orders_user_id_idempotency_key UNIQUE (user_id, idempotency_key),
    -- PENDING 만료·복구 배치가 상태와 경과 시간으로 조회한다.
    INDEX idx_orders_status_updated_at (status, updated_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_items
(
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    order_id       BIGINT         NOT NULL,
    product_id     BIGINT         NOT NULL,
    quantity       INT            NOT NULL,
    -- 주문 시점 가격 스냅샷. 이후 상품 가격이 바뀌어도 금액이 유지된다.
    price_at_order DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id),
    -- InnoDB가 FK 컬럼 인덱스를 자동으로 만드므로 별도 INDEX를 두지 않는다(V2에서 확인된 전제).
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

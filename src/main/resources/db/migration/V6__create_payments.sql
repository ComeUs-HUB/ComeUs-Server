-- Phase 4 · 9단계 — 결제 스키마
--
-- 작성 기준: Payment 엔티티가 생성하는 DDL과 1:1로 맞춘다.
-- ⚠️ 테스트는 H2 create-drop이라 이 파일을 실행하지 않는다 — MySQL 기동(ddl-auto: validate)으로만 확인된다.
--
-- 이 파일은 한 번 적용된 뒤로 수정하지 않는다. 이후 변경은 V7__ … 로 추가한다.

CREATE TABLE payments
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    order_id    BIGINT         NOT NULL,
    -- orders.payment_key와 같은 길이. 조회·취소의 키다.
    payment_key VARCHAR(200)   NOT NULL,
    -- orders.total_amount와 같은 자릿수.
    amount      DECIMAL(14, 2) NOT NULL,
    -- 토스가 준 문자열 그대로("카드", "간편결제" 등). 새 수단이 와도 반영이 실패하지 않게 enum으로 두지 않는다.
    method      VARCHAR(30)    NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    -- 토스 approvedAt
    paid_at     DATETIME(6)    NOT NULL,
    -- 토스 cancels[].canceledAt. 환불 전에는 NULL이다.
    refunded_at DATETIME(6)    NULL,
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- 1 Order : 1 Payment를 DB가 강제한다. FK 인덱스를 겸한다.
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Phase 2 · 2단계 — 회원/리프레시 토큰 초기 스키마
--
-- 작성 기준: 1단계 엔티티(User, RefreshToken)가 생성하는 DDL과 1:1로 맞춘다.
-- ddl-auto: validate 가 이 스키마와 엔티티 매핑을 대조하므로 컬럼명·타입·길이가 어긋나면 기동이 실패한다.
--
-- 이 파일은 한 번 적용된 뒤로 수정하지 않는다. 이후 변경은 V2__, V3__ … 로 추가한다.

CREATE TABLE users
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    -- 카카오 이메일 동의 거부 또는 이미 사용 중인 주소면 NULL.
    -- MySQL은 UNIQUE 컬럼에 NULL 중복을 허용하므로 nullable + unique 조합이 성립한다.
    email        VARCHAR(255) NULL,
    name         VARCHAR(50)  NULL,
    phone        VARCHAR(20)  NULL,
    address      VARCHAR(255) NULL,
    birth_date   DATE         NULL,
    -- enum은 네이티브 ENUM이 아니라 VARCHAR로 둔다. 엔티티의 @JdbcTypeCode(SqlTypes.VARCHAR)와 짝이다.
    role         VARCHAR(20)  NOT NULL,
    provider     VARCHAR(20)  NOT NULL,
    provider_id  VARCHAR(64)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    withdrawn_at DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    -- 동일 카카오 계정 중복 가입 방지
    CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    -- user 1 : token N — 로그아웃이 해당 기기만 무효화하므로 멀티 디바이스 로그인이 전제다
    user_id    BIGINT      NOT NULL,
    -- 토큰 원문이 아니라 SHA-256 hex. 항상 정확히 64자라 CHAR(64)로 고정한다
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

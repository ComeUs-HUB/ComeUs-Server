-- Phase 3 · 2단계 — 카테고리 스키마
--
-- 작성 기준: Category 엔티티가 생성하는 DDL과 1:1로 맞춘다.
-- ddl-auto: validate 가 이 스키마와 엔티티 매핑을 대조하므로 컬럼명·타입·길이가 어긋나면 기동이 실패한다.
-- ⚠️ 테스트는 H2 create-drop(엔티티에서 스키마 생성)이라 이 파일을 실행하지 않는다.
--    즉 여기가 엔티티와 어긋나도 ./gradlew test 는 통과한다 — MySQL 기동으로만 확인된다.
--
-- 이 파일은 한 번 적용된 뒤로 수정하지 않는다. 이후 변경은 V3__, V4__ … 로 추가한다.

CREATE TABLE categories
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    name          VARCHAR(50) NOT NULL,
    -- 대분류면 NULL. 대분류 > 소분류 2단계 구조를 self-join으로 표현한다.
    -- 깊이 제한은 DB가 아니라 Category.child() 정적 팩터리가 강제한다 —
    -- self-join 자체는 3depth 이상도 구조적으로 허용하기 때문이다.
    parent_id     BIGINT      NULL,
    display_order INT         NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 자기 참조 FK. InnoDB가 parent_id 인덱스를 자동으로 만들므로 별도 INDEX를 두지 않는다.
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

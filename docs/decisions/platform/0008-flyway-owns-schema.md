# ADR 0008 — 스키마 소유자는 Flyway (`ddl-auto: validate`)

- 상태: 채택
- 일자: 2026-08-13
- 관련: [architecture.md](../../architecture.md), [roadmap.md](../../roadmap.md)

## 맥락

Phase 2 착수 시점에 스키마 관리 방식을 정해야 했다. Hibernate `ddl-auto: update`로 두면 당장은 편하지만,
운영 DB에 손으로 DDL을 반영하는 작업이 계속 따라온다.

## 결정

**Flyway가 스키마를 소유하고, Hibernate는 검증만 한다.**

- `ddl-auto`를 local·prod 모두 **`validate`로 통일**한다
- `db/migration/V1__init.sql`이 `users`·`refresh_tokens` DDL을 소유한다
- 이후 Phase에서는 `V2__`, `V3__`… **추가만** 하고 기존 파일은 수정하지 않는다
- ⚠️ `src/test/resources/application.yml`에는 `spring.flyway.enabled: false` — 테스트는 H2라 MySQL DDL을
  실행할 수 없다. 테스트 스키마는 `ddl-auto: create-drop`이 엔티티 매핑으로부터 만든다

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `ddl-auto: update` 유지 | 운영 DDL을 손으로 반영해야 한다. 테이블이 늘수록 위험해진다 |
| 나중에 Flyway 도입 | **되돌리기 비용이 시간에 비례해 커진다.** `update` 위에서 Repository를 검증해두면 `validate`로 바꾸는 순간 **이미 통과했던 테스트가 다시 깨진다** — Hibernate가 발명한 스키마와 손으로 쓴 DDL이 다르기 때문이다. 테이블이 2개인 지금이 전환 비용 최소 시점이다 |
| enum `check` 제약을 `V1__init.sql`에 포함 | `validate`가 check 제약을 검사하지 않아 기동에 영향이 없고, 넣으면 **enum 상수를 하나 추가할 때마다 마이그레이션이 강제된다**(`Provider.GOOGLE` 추가 등). 이 컬럼에 쓰는 주체는 Java enum을 다루는 JPA뿐이라 무효값이 들어올 경로가 없다 |

## 결과 / 되돌리려면

`update`에는 **한 번도 의존하지 않았다.** 엔티티(1단계) 직후 스키마를 확정(2단계)했고, Repository 검증(3단계)이
확정된 스키마 위에서 돌았다.

두 가지가 따라온다:

1. ⚠️ **유니크 제약은 `V1__init.sql`과 엔티티 `@Table(uniqueConstraints=...)` 양쪽에 선언한다.**
   `@DataJpaTest`는 H2에서 **엔티티 매핑으로부터** 스키마를 만들기 때문에, SQL에만 쓰면 테스트에서
   재현되지 않아 **중복 가입 테스트가 통과해버린다.**
2. Hibernate 6이 자동 생성하는 enum `check` 제약이 **H2 테스트에만 존재**하게 된다. 무해하다.

검증은 실행 수단과 무관하다 — `flyway.enabled: true` + `ddl-auto: validate` 상태에서 앱이 MySQL에
기동되면 그 자체가 "마이그레이션이 적용됐고 엔티티↔스키마 대조를 통과했다"는 뜻이다.

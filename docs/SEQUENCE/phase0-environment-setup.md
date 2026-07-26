# Phase 0 — 환경 설정 (A~C)

> 상태: 부분 구현 — 프로젝트 골격과 의존성은 준비됐지만 `domain/*`, `global/security` 패키지는 아직 없음

프로젝트 초기 골격(패키지 구조, 의존성, 실행 환경 설정)을 잡는 단계.

---

## A. 프로젝트 초기화

**목적**: Spring Boot 3.5.0 기반 프로젝트 골격을 생성하고 기본 패키지 구조를 잡는다.

**구현 현황 — 부분 구현**

- Gradle(Kotlin DSL) + Java 21 + Spring Boot 3.5.0 골격, `BackendApplication.java`, Git 저장소 초기화까지는 완료.
- 다만 목표로 했던 `com.toyproject.backend.domain.{user,product,category,cart,order,payment,review}` 패키지는 아직 하나도 만들어지지 않았고, `global.security` 패키지도 없음. 현재 존재하는 `global` 하위는 `config`, `exception`, `response` 뿐.
- 도메인 패키지는 뒤에 이어지는 Phase(인증/카테고리·상품/구매 플로우/리뷰) 작업을 시작할 때 그 시점에 필요한 하위 폴더(`controller`, `service`, `repository`, `entity`, `dto`)부터 만들어 나가는 방식을 권장 — 미리 빈 패키지를 다 만들어두기보다 실제 클래스가 생길 때 같이 생성.

**개발 방향**
- 새 도메인 패키지를 만들 때는 기존 `global` 패키지들과의 의존 방향(도메인 → global 참조는 가능, 그 반대는 지양)을 계속 지킬 것.
- `.gitignore`에 `application-*.yml` 등 민감 설정이 이미 제외되어 있는지 새 설정 파일을 추가할 때마다 재확인.

**체크포인트**
- [x] `./gradlew bootRun` 으로 서버 정상 기동 확인
- [ ] 도메인 패키지 구조 생성 완료 (User 도메인부터 시작 — [[phase2-auth]] 참고)

---

## B. build.gradle.kts 의존성 설정

**목적**: 프로젝트에 필요한 모든 라이브러리를 한 번에 정의한다.

**구현 현황 — 구현 완료**

- `spring-boot-starter-web/data-jpa/security/validation`, `mysql-connector-j`, `jjwt-api/impl/jackson:0.12.6`, Lombok, `spring-security-test`가 모두 `build.gradle.kts`에 반영되어 있음.
- 원 설계에는 없던 `h2`(테스트용 인메모리 DB)와 `junit-platform-launcher`(`testRuntimeOnly`)가 추가로 들어가 있음 — 최신 Gradle/JUnit 권장 설정을 선반영한 것.

**개발 방향**
- 이후 도메인 구현 중 추가 의존성이 필요해지면(예: QueryDSL, Redis, S3 SDK) 이 시점에 build.gradle.kts를 갱신 — 미리 추가하지 않는다.

**체크포인트**
- [x] `./gradlew dependencies` 실행 시 의존성 충돌 없음
- [x] Lombok 어노테이션 정상 동작 확인

---

## C. application.yml 설정

**목적**: 개발/운영 환경을 분리하고 DB, JPA, JWT, 서버 포트를 설정한다.

**구현 현황 — 부분 구현**

- 공통 베이스 `application.yml`, 운영용 `application-prod.yml`은 존재. 다만 원래 설계했던 `dev` 프로파일명 대신 실제로는 `local` 프로파일(`application-local.yml`)을 사용 중 — 문서/설정 작성 시 이 이름 차이에 유의.
- `application-prod.yml`은 `.gitignore`로 제외되어 Git에 커밋되지 않음 (설계 의도대로 동작 확인됨).
- `application.yml`에 `jwt.secret`, `jwt.access-expiration`, `jwt.refresh-expiration` 값이 이미 선언되어 있음 — 실제 이를 소비하는 `JwtProvider`는 아직 없음([[phase2-auth]] I 항목에서 구현 예정).
- `src/test/resources/application.yml`에 H2 인메모리 DB(`create-drop`) 설정이 별도로 준비되어 있음 — 테스트 환경 분리가 이미 되어 있다는 뜻이므로 이후 테스트 코드 작성([[phase6-finalization]] S 항목) 시 이 설정을 그대로 활용.
- `management.endpoints`(actuator) 설정은 아직 없음 — 배포 단계의 헬스체크([[phase7-deployment]] V, X, Y 항목)에서 필요해질 때 추가.

**개발 방향**
- 운영 환경 체크리스트(`ddl-auto`, `show-sql`, 로그 레벨, DB 접속 정보의 환경변수화 등)는 [[phase7-deployment]] V 항목에서 다시 다루므로 여기서는 dev/local·prod 분리 원칙만 유지.
- actuator 헬스체크를 추가할 시점과 노출 범위(`health`, `info` 등)는 배포 단계 진입 직전에 결정.

**체크포인트**
- [x] 로컬 환경에서 MySQL 연결 성공
- [x] `application-prod.yml`이 Git에 올라가지 않음 확인
- [ ] actuator 헬스체크 엔드포인트 노출 설정

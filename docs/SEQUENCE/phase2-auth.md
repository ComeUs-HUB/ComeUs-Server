# Phase 2 — 인증/인가 (User 도메인, G~K)

> 상태: 미구현 — `domain/user`, `global/security` 패키지 모두 아직 없음. 단 `ErrorCode`(Auth 관련)와 `application.yml`의 `jwt.*` 설정값은 [[phase1-global-foundation]], [[phase0-environment-setup]]에서 이미 선반영되어 있음

회원 도메인과 JWT 기반 인증/인가의 기반을 만드는 단계. 이후 모든 도메인(Cart, Order, Review 등)이 "로그인한 사용자" 개념에 의존하므로 다른 도메인보다 먼저 진행되어야 한다.

---

## G. User 엔티티 / Repository 구현

**목적**: 회원 도메인의 핵심 엔티티를 설계하고 JPA 기반 데이터 접근 계층을 구성한다.

**개발 방향**
- `domain/user` 패키지를 새로 만들며 `entity`, `repository`, `service`, `controller`, `dto` 하위 구조를 함께 구성.
- 이메일 중복 체크가 회원가입 로직의 핵심이므로 Repository 단에서 이메일 존재 여부를 빠르게 확인할 수 있는 방법을 마련할 것 (유니크 제약 + 조회 메서드).
- Role(USER/ADMIN) 구분을 엔티티 단에 둘지, 별도 권한 테이블로 분리할지 결정 필요 — 초기 단계에서는 단순 Enum 컬럼으로 충분한지 검토.
- 생성/수정 시각 자동 기록(Auditing)이 필요한지 판단 — 이미 `global/config/JpaConfig.java`에 `@EnableJpaAuditing`이 활성화되어 있으므로 `@CreatedDate`/`@LastModifiedDate` 어노테이션만 붙이면 재사용 가능.

**체크포인트**
- [ ] DDL 자동 생성으로 회원 테이블 정상 생성
- [ ] 이메일 중복 조회 쿼리 정상 동작

---

## H. Spring Security 기반 설정

**목적**: Spring Security 6.x 방식(`SecurityFilterChain` Bean)으로 인증/인가 기반을 완성한다.

**개발 방향**
- Spring Boot 3.x부터 `WebSecurityConfigurerAdapter`가 제거되었으므로 `SecurityFilterChain` Bean 등록 방식으로 설계.
- 엔드포인트별 접근 정책(비회원 허용/인증 필요/ADMIN 전용)을 표로 먼저 정리한 뒤 필터체인에 반영하는 순서를 권장 — 최종 정책 매트릭스는 [[phase6-finalization]] R 항목에서 다시 점검.
- 인증 실패(401)와 인가 실패(403) 응답도 프로젝트 공통 응답 포맷(`ApiResponse`, [[phase1-global-foundation]] D 항목)을 따르도록 진입점/핸들러를 분리해서 구성할지 결정.
- 세션을 사용하지 않는 stateless 정책(JWT 기반)을 취할지 여부를 이 단계에서 확정 — 이후 인증 필터(J 항목) 설계에 영향을 줌.

**체크포인트**
- [ ] 비회원 허용 API 정상 접근 확인
- [ ] 인증 필요 API에 비인증 접근 시 401 반환
- [ ] ADMIN 전용 API에 일반 회원 접근 시 403 반환

---

## I. JWT 유틸리티 구현 (JwtProvider)

**목적**: Access Token / Refresh Token의 생성·검증·파싱을 담당하는 컴포넌트를 만든다.

**개발 방향**
- jjwt 0.12.x 버전 API 기준으로 구현 (`build.gradle.kts`에 이미 `jjwt-api/impl/jackson:0.12.6` 반영됨, [[phase0-environment-setup]] B 항목 참고).
- `application.yml`에 이미 `jwt.secret`, `jwt.access-expiration`, `jwt.refresh-expiration` 값이 선언되어 있으므로 이를 그대로 주입받아 사용.
- 토큰 만료/위조 시 어떤 에러코드로 응답할지는 이미 `ErrorCode`에 `INVALID_TOKEN`, `EXPIRED_TOKEN`이 정의되어 있으므로 그대로 활용.
- Refresh Token 저장 전략 결정 필요: 우선 DB 테이블에 저장하는 방식으로 시작하고, 추후 Redis 도입 시 교체 가능하도록 인터페이스 수준에서 분리할지 여부를 판단(PRD 미결 사항, [[phase7-deployment]] Z 항목의 후속 과제와 연결).
- Refresh Token Rotation(재발급 시 기존 토큰 갱신) 정책을 적용할지 결정.

**체크포인트**
- [ ] Access Token 만료 시간이 설정값(30분)과 일치하는지 확인
- [ ] Refresh Token 저장/조회 정상 동작

---

## J. JWT 인증 필터 구현 (JwtAuthenticationFilter)

**목적**: 모든 요청에서 JWT를 추출·검증하여 `SecurityContext`에 인증 정보를 채운다.

**개발 방향**
- 요청 헤더에서 토큰을 추출하는 위치(`Authorization: Bearer ...`)와 실패 시 처리(토큰 없음 → 그냥 다음 필터로 통과 vs 즉시 거부)를 H 항목의 필터체인 설계와 맞춰 결정.
- 필터 체인 내 위치(`UsernamePasswordAuthenticationFilter` 이전 등)를 H 항목에서 구성한 `SecurityFilterChain`과 함께 정리.

**체크포인트**
- [ ] 유효한 토큰으로 인증 정상 통과
- [ ] 토큰 없이 인증 필요 API 요청 시 401 반환

---

## K. 인증 API 구현 (AuthController / UserController)

**목적**: 회원가입, 로그인, 토큰 재발급, 로그아웃, 내 정보 CRUD API를 완성한다.

**개발 방향**
- 필요한 엔드포인트: 회원가입, 로그인(Access+Refresh 발급), 토큰 재발급, 로그아웃, 내 정보 조회/수정/탈퇴 — 정확한 URI와 메서드는 API 설계 시 `docs/ARCHITECTURE.md`의 API 스펙 규칙을 따를 것.
- 비밀번호 저장 시 반드시 단방향 해시(BCrypt 등)로 암호화 후 저장 — 평문 저장 금지.
- 로그인한 사용자의 식별 방식(예: `@AuthenticationPrincipal`)을 H/J 항목의 Security 설정과 일관되게 사용.
- 회원 탈퇴 시 연관된 데이터(주문 이력, 리뷰 등)를 물리 삭제할지 비활성화 처리할지 정책 결정 필요 — 다른 도메인 설계([[phase3-category-product]], [[phase4-purchase-flow]])와 함께 검토.

**체크포인트**
- [ ] 회원가입 → 로그인 → 내 정보 조회 전체 플로우 통과
- [ ] 만료된 Access Token + 유효한 Refresh Token으로 재발급 성공
- [ ] 로그아웃 후 이전 Refresh Token 재사용 시 에러 반환

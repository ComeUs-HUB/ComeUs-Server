# Clothing Store Backend API — 프로젝트 진행 순서 (A~Z)

> **기술 스택**: Spring Boot 3.5.0 · Java 21 · JPA · MySQL · Gradle (Kotlin DSL)
> **목표**: 의류 판매 웹사이트 REST API 서버 구축 및 배포
> **작성 기준**: PRD v1.0 기반

이 문서는 전체 Phase 요약 인덱스입니다. 각 Phase의 세부 작업 내용(개발 방향 제안, 이미 구현된 항목의 실제 구현 방식, 체크포인트)은 `docs/SEQUENCE/` 하위 파일에 분리되어 있습니다.

---

## 전체 Phase 요약

| Phase | 단계 | 내용 | 상태 | 상세 문서 |
|-------|------|------|------|-----------|
| 0 | A ~ C | 환경 설정 — 프로젝트 초기화, 의존성, 설정 파일 | 부분 구현 | [phase0-environment-setup.md](SEQUENCE/phase0-environment-setup.md) |
| 1 | D ~ F | 글로벌 기반 — 공통 응답, 예외 처리, 페이지네이션 | 구현 완료 | [phase1-global-foundation.md](SEQUENCE/phase1-global-foundation.md) |
| 2 | G ~ K | 인증/인가 — user/auth 도메인, Security, JWT | 구현 완료 (9단계 브라우저 E2E 확인 대기) | [phase2-auth.md](SEQUENCE/phase2-auth.md) |
| 3 | L ~ M | 카테고리/상품 — Category, Product 도메인 | 미구현 | [phase3-category-product.md](SEQUENCE/phase3-category-product.md) |
| 4 | N ~ P | 구매 플로우 — Cart, Order, Payment 도메인 | 미구현 | [phase4-purchase-flow.md](SEQUENCE/phase4-purchase-flow.md) |
| 5 | Q | 리뷰 — Review 도메인 | 미구현 | [phase5-review.md](SEQUENCE/phase5-review.md) |
| 6 | R ~ S | 마무리 — RBAC 완성, 테스트 코드 | 미구현 | [phase6-finalization.md](SEQUENCE/phase6-finalization.md) |
| 7 | T ~ Z | 배포 — Docker, CI/CD, 서버 배포, 모니터링 | 미구현 | [phase7-deployment.md](SEQUENCE/phase7-deployment.md) |

현재 코드베이스는 **Phase 0~2(A~K)까지 구현**되어 있다. Phase 2는 카카오 OAuth 로그인·JWT 인증·회원 API·탈퇴가
모두 동작하며 자동 테스트 131개가 붙어 있고, 9단계의 브라우저 E2E 확인만 남았다. 다음은 Phase 3(L, Category)이다.

---

## 진행 순서 체크리스트 요약

```
[phase0-environment-setup.md] 환경 설정
 ☑ A. 프로젝트 초기화 (부분 구현 — domain 패키지 미생성)
 ☑ B. build.gradle.kts 의존성 설정 (구현 완료)
 ☑ C. application.yml 환경별 설정 (부분 구현 — dev 대신 local 프로파일 사용 중)

[phase1-global-foundation.md] 글로벌 기반
 ☑ D. ApiResponse 공통 응답 래퍼 (구현 완료)
 ☑ E. GlobalExceptionHandler + ErrorCode + BusinessException (구현 완료)
 ☑ F. PageResponse 페이지네이션 공통 구조 (구현 완료, 사용처는 아직 없음)

[phase2-auth.md] 인증/인가 (선행 필수)
 ☑ G. User/RefreshToken 엔티티 + Repository + 카카오 연동 인프라 + Flyway 도입 (구현 완료)
 ☑ H. SecurityFilterChain + 401/403 핸들러 + CORS(쿠키 허용) (구현 완료)
 ☑ I. JwtProvider (Access Token) + Refresh Token 발급·검증 (rotation 미적용) (구현 완료)
 ☑ J. JwtAuthenticationFilter + CustomUserDetailsService (구현 완료)
 ☑ K. AuthController + UserController (카카오 로그인 플로우 전체) (구현 완료 — 9단계 브라우저 E2E 확인 대기)

[phase3-category-product.md] 카테고리/상품
 ☐ L. Category 도메인 (계층 구조, 트리 조회, 삭제 방어)
 ☐ M. Product 도메인 (페이징·필터·정렬, 품절 자동 전환, soft delete)

[phase4-purchase-flow.md] 구매 플로우
 ☐ N. Cart 도메인 (수량 합산, 재고 초과 방어)
 ☐ O. Order 도메인 (동시성 제어, 가격 스냅샷, 상태 흐름)
 ☐ P. Payment 도메인 (결제 처리, 주문 상태 전환, 환불)

[phase5-review.md] 리뷰
 ☐ Q. Review 도메인 (작성 조건 검증, 중복 방지, 평균 평점)

[phase6-finalization.md] 마무리
 ☐ R. RBAC 권한 설정 완성 + 401/403 응답 포맷 통일
 ☐ S. 테스트 코드 (단위 + 통합)

[phase7-deployment.md] 배포
 ☐ T. Dockerfile (멀티 스테이지 빌드)
 ☐ U. docker-compose.yml (app + MySQL + 볼륨 + 헬스체크)
 ☐ V. 환경별 설정 분리 (prod 프로파일 + actuator)
 ☐ W. CI/CD 파이프라인
 ☐ X. 서버 배포 (리버스 프록시 + HTTPS)
 ☐ Y. 배포 후 E2E 최종 검증
 ☐ Z. 운영 모니터링 + 보안 점검 + DB 백업
```

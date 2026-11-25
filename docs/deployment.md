# 배포

컨테이너화 → CI/CD → 서버 배포 → 운영까지. **배포 파이프라인은 아직 없다** —
Dockerfile과 docker-compose.yml이 부재하고, `.github/workflows`에는 코드 리뷰 워크플로 하나뿐이다.
진행 현황은 [roadmap.md](roadmap.md)가 소유한다.

## ⚠️ 배포 형태 제약 — 먼저 읽을 것

**FE는 BE와 동일 상위 도메인의 서브도메인으로 배포한다** (예: `app.example.com` / `api.example.com`).

`refresh_token` 쿠키가 `SameSite=Strict`이므로, FE를 별도 사이트(Vercel·Netlify 등)로 분리하면
브라우저가 cross-site 요청에 쿠키를 **아예 첨부하지 않아 `/api/auth/refresh`·`/logout`이 전면
동작 불능**이 된다. 보안이 약해지는 게 아니라 **인증 기능이 파손된다.**

분리 배포가 불가피해지면 **`refresh_token`의** `SameSite=None; Secure` 전환과
**CSRF 토큰 도입을 함께** 진행해야 한다. (`oauth_state`는 kakao→BE 구간이라 FE 배포 위치와 무관하다 —
계속 `Lax`로 둔다.)
근거: [ADR 0006](decisions/auth/0006-cookie-samesite-and-path.md).

이것이 배포 방식이 인증 설계를 되짚는 유일한 지점이므로, 호스팅을 고르기 전에 확인한다.

### 서버를 여러 대로 늘릴 때 — 스케줄러

`@Scheduled` 작업 두 개(`PENDING` 만료, 결제 복구)는 **인스턴스마다 돈다.** 분산 락(ShedLock 등)은 없다.

- 상태 전환은 모두 CAS라 겹쳐 돌아도 **결과는 한 번만 반영된다.** 복구 배치는 읽은 `updated_at`까지 비교한다([ADR 0030](decisions/purchase/0030-recovery-batch-runtime-limits.md))
- 대신 토스 조회가 인스턴스 수만큼 나가고, 1시간 미판정 경보가 **인스턴스 수만큼 중복된다.** 줄이는 방법은 [payment.md의 복구 배치 규칙](domains/purchase/payment.md#규칙)
- 단일 서버로 시작하면 해당 없다. 늘리기 전에 경보 중복을 받아들일지, 락을 둘지 정한다

## Dockerfile

최소 크기 이미지를 만든다.

- **멀티 스테이지 빌드** — 빌드 도구(JDK, Gradle)가 최종 실행 이미지에 남지 않게 한다
- 실행 이미지는 **JRE만 포함된 경량 베이스**, 컨테이너 내부에서 **root가 아닌 전용 사용자**로 실행
- 진입점은 `prod` 프로파일로 기동

확인할 것:
- [ ] 이미지 빌드 성공
- [ ] 컨테이너 기동 후 헬스체크 정상 응답
- [ ] 이미지 크기 최소화

## docker-compose.yml

로컬 개발·단일 서버 배포에서 앱과 MySQL을 함께 관리한다.

- 앱이 DB의 준비 상태를 기다리도록 **헬스체크 기반 의존성 순서**를 건다
- DB 데이터는 **볼륨으로 분리**해 컨테이너 재시작 후에도 유지
- DB 접속 정보·JWT 시크릿·토스 시크릿 키는 `.env`로 분리 (이미 `.gitignore` 대상)

확인할 것:
- [ ] DB 준비 전에 앱이 먼저 뜨지 않음
- [ ] 컨테이너 재시작 후 데이터 유지
- [ ] `.env`가 Git에 포함되지 않음

## prod 프로파일

`application-prod.yml` 자체는 이미 있고 `.gitignore`로 보호되고 있다. actuator 설정만 없다.

| 항목 | local | prod |
|---|---|---|
| `ddl-auto` | `validate` | `validate` |
| `flyway.enabled` | `true` | `true` |
| `show-sql` | `true` | **`false`** |
| `app.cookie.secure` | `false` | **`true`** |
| 로그 레벨 | debug/trace | 낮춤 |

- **fail-fast**: 필수 환경변수(DB 접속 정보, `JWT_SECRET` 등)가 없으면 조용히 잘못된 값으로 뜨지 말고
  기동 자체가 실패하게 만든다
- actuator는 필요한 것만 최소 노출 (`health`, `info` 정도)

### 누락하면 기동이 실패하는 값

안전한 기본값을 정할 수 없는 값이라 코드가 거부한다
([ADR 0023](decisions/platform/0023-fail-fast-when-no-safe-default.md)).

| 설정 | 거부하는 곳 | 비어 있으면 |
|---|---|---|
| `jwt.secret` (`JWT_SECRET`) | `JwtProvider` 생성자 | `WeakKeyException` — 32바이트 미만도 거부 |
| `app.frontend.base-url` | `AppProperties` 생성자 | `IllegalArgumentException` |

`toss.secret-key`(`TOSS_SECRET_KEY`)는 기동을 막지 않는다. 비어 있으면 토스 호출이 전부 `UNAUTHORIZED_KEY`로 실패해
결과 불명(경보)이 되므로, 배포 후 첫 결제 전에 반드시 확인한다.

나머지(`app.cors.allowed-origins`, `app.cookie.secure`)는 비어 있으면 **안전한 쪽 기본값**으로
흡수되고 기동은 된다 — 각각 "모든 cross-origin 차단", `Secure=true`다.

⚠️ `application-prod.yml`에는 현재 `app.cookie.secure`만 있다. 배포 전에 위 두 값과
`kakao.*`(client-id·client-secret·admin-key·redirect-uri), `toss.secret-key`, `app.cors.allowed-origins`를
프로파일 파일이나 환경변수로 채워야 한다.

확인할 것:
- [ ] prod 프로파일에서 SQL 로그 미출력
- [ ] 필수 환경변수 누락 시 기동 실패
- [ ] `app.frontend.base-url`·`kakao.*`·`app.cors.allowed-origins`가 prod에 채워져 있음
- [ ] actuator 헬스체크 엔드포인트 노출

## CI/CD

- PR 생성 시 **빌드 + 테스트**만, `master` 반영 시 **이미지 빌드·푸시·배포**까지
- Gradle 의존성 빌드 캐시로 실행 시간 단축
- DB 접속 정보, 배포 서버 접속키, 레지스트리 인증 정보는 **모두 CI 시크릿 저장소로 주입**한다.
  코드·워크플로 파일에 직접 쓰지 않는다

확인할 것:
- [ ] PR에서 빌드/테스트 자동 실행
- [ ] `master` 반영 시 자동 배포
- [ ] 파이프라인 전체 단계 통과

## 서버 배포

- 애플리케이션 포트를 외부에 직접 노출하지 않고 **리버스 프록시를 통해서만** 접근하도록 방화벽 구성
- HTTPS 인증서 발급·자동 갱신 방식 결정
- HTTP → HTTPS 강제 리다이렉트
- ⚠️ **카카오 개발자 콘솔에 prod Redirect URI 등록** — 누락하면 카카오가 `KOE006`으로 거부해
  운영에서 로그인이 첫 시도부터 실패한다. local만 등록돼 있다

**앱은 `X-Forwarded-*`에 기대지 않는다.** 201 응답의 `Location`을 상대 경로로 내보내고,
그 값은 **받는 쪽이 자기 요청 URI 기준으로 해석**하므로 프록시가 스킴·호스트를 전달하지 않아도
응답이 깨지지 않는다. 절대 URL을 골랐다면 `server.forward-headers-strategy`나 nginx의
`proxy_redirect` 중 하나를 반드시 설정해야 했다
([conventions/api-response.md](conventions/api-response.md)).
반대로 카카오 로그인의 302는 절대 URL이 필요한데, 그 값은 요청에서 추론하지 않고
`app.frontend.base-url`·`kakao.redirect-uri` 설정에서 읽는다 — prod 값이 틀리면 로그인이 깨지고,
`app.frontend.base-url`은 **아예 없으면 기동이 실패한다**(위 "누락하면 기동이 실패하는 값")

확인할 것:
- [ ] 헬스체크가 HTTPS로 정상 응답
- [ ] HTTP → HTTPS 리다이렉트
- [ ] 애플리케이션 포트 외부 직접 접근 차단
- [ ] FE/BE 동일 상위 도메인 배포 후 refresh 쿠키 정상 왕복
      (로그인 → 새로고침 → silent refresh 성공)

## 배포 후 검증

회원가입 → 로그인 → 카테고리·상품 등록(ADMIN) → 상품 조회(비회원) → 장바구니 → 주문 → 결제 →
배송완료 처리 → 리뷰 작성 → 토큰 재발급 → 로그아웃까지 이어지는 시나리오를 순서대로 검증한다.

전 API가 [공통 응답 포맷](conventions/api-response.md)과 `PageResponse` 구조를 지키는지 함께 확인한다.

토스는 **상점 키(라이브 또는 상점 테스트 키)로 다시 확인한다.** 로컬 확인은 문서용 공개 테스트 키로 했다
([payment.md의 테스트 키 확인 결과](domains/purchase/payment.md#테스트-키-확인-결과)). 승인·조회·전액 취소와 거절 코드 분류가 같은지 본다.

확인할 것:
- [ ] 전체 시나리오 통과
- [ ] 상점 키로 승인 · 조회 · 전액 취소 확인
- [ ] 응답 포맷 전 API 준수

## 운영 · 보안

- 로그 보관 기간과 롤링 정책(파일 크기/기간 기준) 결정
- 보안 점검 항목:
  - `JWT_SECRET`이 충분한 길이인지 + 환경변수로 주입되는지 (소스·Git 어디에도 없어야 한다)
  - 전 구간 HTTPS 강제
  - JPA 파라미터 바인딩으로 SQL 인젝션 방지
  - 에러 응답에 스택 트레이스 미노출
  - 로그인 API 무차별 대입 방어 (Rate Limiting)
  - DB 정기 백업

확인할 것:
- [ ] 운영 서버 에러 로그 모니터링 체계
- [ ] `JWT_SECRET`·`TOSS_SECRET_KEY`가 소스코드·Git에 노출되지 않음
- [ ] DB 백업 스크립트 동작

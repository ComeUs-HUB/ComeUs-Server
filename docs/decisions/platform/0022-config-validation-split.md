# ADR 0022 — 설정 누락은 서명 키만 기동에서 막고, 나머지는 안전한 기본값으로 흡수한다

- 상태: 채택
- 일자: 2026-10-29
- 관련: [architecture.md](../../architecture.md), [ADR 0006](../auth/0006-cookie-samesite-and-path.md)

## 맥락

`@ConfigurationProperties` 세 묶음(`jwt`, `app`, `kakao`)이 환경별 yml에서 값을 받는데,
값이 비었을 때의 동작이 묶음마다 다르다. `AppProperties`와 `JwtProperties`의 Javadoc은 이 차이를
"결정 필요"로 남기고 *"두 곳의 방침을 함께 정하는 편이 좋다"*고 적어 두었다.

세 묶음 모두 `@Validated`를 붙이지 않는다. `src/test/resources/application.yml`이 main 설정을
shadow하므로 검증을 걸면 테스트 컨텍스트가 먼저 깨진다.

## 결정

**누락 시 동작을 값의 성격으로 가른다.** 묶음 단위가 아니다.

| 성격 | 누락 시 | 지금 그렇게 동작하는 곳 |
|---|---|---|
| **서명 키·자격증명** | **기동 실패(fail-fast)** | `jwt.secret` — `JwtProvider` 생성자의 `Keys.hmacShaKeyFor`가 32바이트 미만이면 `WeakKeyException`을 던지고, 빈 생성 시점이라 앱이 뜨지 않는다 |
| **환경 의존 값** | **안전한 기본값(fail-safe)** | `app.cors.allowed-origins` → 빈 목록(모든 cross-origin 차단), `app.cookie.secure` → `true` |

근거는 **잘못된 값으로 서비스가 뜨는 것이 더 위험한가**다.

- 서명 키가 약하면 토큰을 위조할 수 있다. 짧은 키로 뜬 서버는 "동작하는데 안전하지 않은" 상태이고,
  이 결함은 요청이 들어와도 드러나지 않는다. 기동에서 막는 것이 유일한 발견 시점이다.
- CORS origin 목록은 FE 배포 주소에 따라 달라지고, 비어 있으면 **차단** 쪽으로 실패한다.
  여기서 기동을 막으면 FE 주소 하나를 빠뜨렸을 때 모바일 앱·서버 간 호출·헬스체크까지 함께 죽는다
  — CORS는 브라우저만 적용받는 제약이라 과한 대가다.
- `app.cookie.secure`는 래퍼 `Boolean`으로 받아 `null`을 `true`로 흡수한다. `boolean`이면
  `app.cookie`만 있고 `secure`가 없을 때 조용히 `false`로 바인딩돼 `Secure`가 꺼지는 쪽으로 실패한다.

`AppProperties`의 compact 생성자가 중첩 record의 `null`을 흡수하는 것도 이 결정의 일부다.
`app.*`가 통째로 없는 환경에서 `SecurityConfig` 기동 중 NPE가 나는 것을 막는다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 전부 `@Validated` + `@NotBlank`/`@Size` | 테스트 yml이 main을 shadow해 테스트 컨텍스트가 먼저 깨진다. 운영 안전을 위해 테스트를 전부 손보는 거래가 성립하지 않는다 |
| prod 프로파일에서만 `@Validated`를 켠다 | 검증이 걸리는 경로와 걸리지 않는 경로가 생겨, 정작 **운영에서만** 처음 실행되는 코드가 된다. 기동 실패를 배포 시점에 처음 보게 된다 |
| 전부 fail-fast (기동 시 경고 아니라 중단) | 위의 CORS 사례. 브라우저 전용 제약 때문에 API 전체를 세운다 |
| 전부 fail-safe (서명 키도 흡수) | 임의 키를 생성해 띄우면 재시작마다 모든 토큰이 무효가 되고, 그 사실이 로그 한 줄로만 남는다 |

## 결과 / 되돌리려면

**이 정책으로 설명되지 않는 곳이 하나 있다.** `app.frontend.base-url`은 누락 시 `null`로 남고,
`AuthController.frontendCompleteUri()`가 문자열로 이어 붙여 `"null/oauth/complete"`로 리다이렉트한다.
fail-fast도 fail-safe도 아닌 **조용한 오동작**이다. 이 ADR은 그 동작을 비준하지 않는다 —
`base-url`은 서명 키와 같은 "없으면 기능이 성립하지 않는 값"이므로 fail-fast 쪽에 속하고,
기동 검증을 붙이는 것이 맞다. 별도 작업으로 다룬다.

되돌리는 조건: 테스트 설정이 main을 shadow하지 않게 분리되면 `@Validated`를 전면 적용하는 선택지가
되살아난다. 그때 이 결정을 재검토한다.

검증: `jwt.secret`을 32바이트 미만으로 두면 컨텍스트 로딩이 실패한다.
`app.cors.allowed-origins`를 비우면 `JwtAuthenticationIntegrationTest`의 preflight 케이스가
차단 쪽으로 동작한다.

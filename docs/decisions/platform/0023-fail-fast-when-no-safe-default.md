# ADR 0023 — 안전한 기본값을 정할 수 없는 설정은 기동에서 막는다

- 상태: 채택
- 일자: 2026-10-30
- 보강: [ADR 0022](0022-config-validation-split.md)
- 관련: [architecture.md](../../architecture.md), [domains/auth.md](../../domains/auth.md)

## 맥락

[ADR 0022](0022-config-validation-split.md)는 설정 누락 시의 동작을 **값의 성격**으로 갈랐다 —
`서명 키·자격증명`은 기동 실패, `환경 의존 값`은 안전한 기본값.

그 분류로 설명되지 않는 값이 하나 있었고, 0022가 직접 적어 두었다.
`app.frontend.base-url`이 누락되면 `AuthController.frontendCompleteUri()`가 문자열을 이어 붙여
**`"null/oauth/complete"`로 302**한다. 예외도 로그도 없이 로그인 왕복 끝에 사용자가 엉뚱한 곳으로 간다.
0022는 이 동작을 비준하지 않고 "fail-fast 쪽에 속하며 별도 작업으로 다룬다"고 남겼다.

이 ADR이 그 후속이다. 구현하면서 드러난 것은, **0022의 분류 축 자체가 `base-url`을 담지 못한다**는 점이다.
`base-url`은 자격증명이 아니고, 그렇다고 안전한 기본값을 가질 수 있는 환경 의존 값도 아니다.

## 결정

**가르는 축을 "값의 성격"에서 "안전한 기본값이 존재하는가"로 바꾼다.**

| 안전한 기본값 | 누락 시 | 해당 값 |
|---|---|---|
| **없다** | **기동 실패(fail-fast)** | `jwt.secret`, `app.frontend.base-url` |
| **있다** | 그 값으로 흡수(fail-safe) | `app.cors.allowed-origins` → 빈 목록, `app.cookie.secure` → `true` |

"안전한 기본값"은 **빠뜨렸을 때 손해가 덜 나는 쪽**을 뜻한다.

- `cors.allowed-origins`가 비면 모든 cross-origin이 **차단**된다. 막히는 쪽으로 실패하고,
  CORS는 브라우저만 적용받으므로 서버 간 호출·헬스체크는 살아 있다. 기동을 막을 이유가 없다.
- `cookie.secure`의 기본값은 `true`다. 빠뜨렸을 때 `Secure`가 **켜지는** 쪽으로 실패한다.
- `jwt.secret`은 임의 키를 만들어 띄울 수 있지만, 재시작마다 모든 토큰이 무효가 되고
  그 사실이 로그 한 줄로만 남는다. 안전한 기본값이 아니다.
- `base-url`은 어떤 값을 넣어도 **틀린 주소**다. FE 배포 주소는 추측할 수 있는 종류의 값이 아니다.

이 축은 0022가 내린 네 값의 결론을 **하나도 바꾸지 않으면서** `base-url`까지 설명한다.
0022의 결론은 유효하고, 분류 근거만 더 정확해진다.

### 구현

`AppProperties`의 compact 생성자가 거부한다. `@ConfigurationProperties` 바인딩 중 예외이므로
컨텍스트 로딩이 실패한다.

```java
if (!StringUtils.hasText(frontend.baseUrl())) {
    throw new IllegalArgumentException("app.frontend.base-url은 반드시 설정해야 합니다.");
}
```

`null`과 공백을 모두 거부한다. 다른 중첩 record의 `null` 흡수는 0022대로 그대로 둔다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `AuthController`에서 쓸 때 검사 | `jwt.secret`이 `JwtProvider` 생성자에서 걸리는 것과 모양은 같지만, 그쪽은 **키를 만드는 지점**이라 검사가 부수적으로 따라온다. `base-url`은 쓰는 지점이 302 조립 한 곳뿐이고 그때는 이미 요청 처리 중이라 "기동에서 드러난다"가 성립하지 않는다 |
| 0022의 "값의 성격" 축을 유지하고 `base-url`만 예외로 추가 | 예외가 하나 붙는 순간 분류가 설명력을 잃는다. 네 값을 일관되게 가르는 축이 있는데 예외를 둘 이유가 없다 |
| `@Validated` + `@NotBlank`로 선언 | 0022가 배제한 그대로다 — 테스트 yml이 main을 shadow해 테스트 컨텍스트가 먼저 깨진다 |
| 그대로 두고 문서로만 경고 | 0022가 이미 "비준하지 않는다"고 적었다. 문서에 적는 것으로 끝내면 같은 결함이 그대로 남는다 |

## 결과 / 되돌리려면

0022는 **대체되지 않는다.** CORS·`cookie.secure`·`@Validated` 미적용 결정은 그대로 유효하고,
이 ADR은 그 분류 축을 정밀하게 바꾸면서 `base-url` 항목을 해소할 뿐이다.

새로 설정 값을 추가할 때는 **"빠뜨렸을 때 안전한 쪽으로 실패할 기본값이 있는가"** 를 먼저 묻는다.
없으면 `AppProperties` 생성자에서 거부하고, 있으면 그 값으로 흡수한다.

되돌리는 조건은 0022와 같다 — 테스트 설정이 main을 shadow하지 않게 분리되면
`@Validated` 전면 적용이 되살아나고, 그때 두 ADR을 함께 재검토한다.

검증: `AppPropertiesTest` 7케이스 — `app.frontend`가 통째로 없을 때·`base-url`이 `null`일 때·
공백일 때 각각 거부하고, `cors`·`cookie`는 기본값으로 흡수하는 것을 함께 고정한다.

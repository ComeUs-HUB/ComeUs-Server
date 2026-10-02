# ADR 0006 — 쿠키 2종의 `SameSite`·`Path`를 다르게 둔다

- 상태: 채택
- 일자: 2026-08-25
- 관련: [ADR 0002](0002-backend-owns-redirect-uri.md), [ADR 0005](0005-opaque-refresh-token-hashed-in-db.md), [domains/auth.md](../../domains/auth.md), [deployment.md](../../deployment.md)

## 맥락

인증 흐름에 쿠키가 두 종류 필요하다 — 세션을 나르는 `refresh_token`과 CSRF 방어용 `oauth_state`다.
"쿠키 속성은 보수적으로 통일한다"가 직관적이지만, 이 둘은 **쓰이는 맥락이 다르다.**

## 결정

**`SameSite`와 `Path`를 서로 다르게 둔다.** 둘 다 `HttpOnly` + `Domain` 미설정(host-only)은 공통이고,
`Secure`는 환경별 설정(`app.cookie.secure`)을 따른다.

| | `refresh_token` | `oauth_state` |
|---|---|---|
| `SameSite` | `Strict` | **`Lax`** |
| `Path` | `/api/auth` | **`/api/auth/kakao/callback`** |
| `Max-Age` | 7일 | **5분** |
| 수명 | 로그아웃·탈퇴까지 | 콜백에서 1회 사용 후 즉시 삭제 |

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| **둘 다 `SameSite=Strict`** | **로그인이 전부 실패한다.** 콜백은 `kauth.kakao.com` → API 호스트로의 cross-site top-level GET 네비게이션인데, `Strict` 쿠키는 여기서 전송되지 않는다. `Lax`는 top-level GET에 한해 전송되므로 `oauth_state`는 반드시 `Lax`여야 한다 |
| 둘 다 `SameSite=Lax` | `refresh_token`은 FE(`app.`) → API(`api.`)의 same-site XHR에만 쓰인다. 굳이 느슨하게 둘 이유가 없다 |
| `oauth_state`도 `Path=/api/auth` | 만료 전 5분 동안 `/refresh`·`/logout`에도 실려 나간다. 인가 왕복이 끝난 뒤에는 아무 쓸모가 없는 값이다 |
| `oauth_state` 수명을 늘리기 | 탈취된 state의 유효 창이 그만큼 길어진다. **CSRF 방어값의 수명은 짧을수록 좋다** |
| `Secure`를 항상 `true` | **Safari는 평문 HTTP에서 `Secure` 쿠키를 거부**해 `oauth_state`가 저장되지 않고 콜백이 전부 A009로 실패한다. Chrome·Firefox는 `http://localhost`를 신뢰 출처로 보고 허용해 문제가 드러나지 않는다 |

`oauth_state` 5분은 **유지하기로 확정**했다. 첫 로그인처럼 카카오 화면에서 오래 머물면 만료돼 A009로
떨어지지만(실제 E2E에서 겪었다), 재시도하면 해결되는 실패다.

## 결과 / 되돌리려면

세 가지 함정이 딸려 온다:

1. ⚠️ **쿠키 삭제 시에도 발급할 때와 같은 `Path`를 반드시 지정한다.** `Max-Age=0`만 내리면 Path가 `/`로
   해석돼 매칭되지 않아 **쿠키가 지워지지 않는다.**
2. 콜백 응답에서 `refresh_token`을 **심는 것**은 SameSite와 무관하게 항상 동작한다 — SameSite는 브라우저가
   쿠키를 *보낼 때*만 관여한다.
3. ⚠️ **`SameSite=Strict`가 배포 형태를 제약한다.** FE/BE를 동일 상위 도메인의 서브도메인으로 배포해야 한다
   (`app.example.com` / `api.example.com`). 분리하면 `/refresh`·`/logout`이 **동작 불능**이 된다 —
   보안 약화가 아니라 인증 기능 파손이다. 자세한 내용은 [deployment.md](../../deployment.md).

CSRF 토큰을 두지 않는 근거도 여기서 나온다: `SameSite=Strict`가 cross-site 요청에 쿠키를 붙이지 않아
원천 차단된다. 위 배포 전제가 깨져 `SameSite=None`으로 가면 **CSRF 토큰 도입이 필수가 된다.**

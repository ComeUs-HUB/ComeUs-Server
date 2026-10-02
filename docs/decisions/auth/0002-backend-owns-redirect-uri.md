# ADR 0002 — `redirect_uri`는 백엔드가 소유한다

- 상태: 채택
- 일자: 2026-08-25
- 관련: [ADR 0001](0001-kakao-only-oauth.md), [ADR 0003](0003-no-pkce-yet.md), [ADR 0006](0006-cookie-samesite-and-path.md), [domains/auth.md](../../domains/auth.md)

## 맥락

카카오 OAuth의 `redirect_uri`를 프론트엔드 주소로 둘지, 백엔드 콜백 주소로 둘지 정해야 했다.
흔한 SPA 예제는 FE 주소를 쓰고, FE가 인가 코드를 받아 BE로 전달한다.

## 결정

**`redirect_uri`는 BE 콜백(`/api/auth/kakao/callback`)을 가리킨다.** FE는 인가 코드를 다루지 않는다.

- FE의 로그인 버튼은 `location.href = '{API_BASE}/api/auth/kakao/authorize'` — **페이지 이동**이다
- authorize URL 조립도, 콜백 `code` 추출도 FE 몫이 아니다
- 콜백은 JSON을 반환하지 않고 항상 302로 `{FE}/oauth/complete`에 착지시킨다
- access token은 이 흐름에서 발급하지 않는다 — FE가 `/api/auth/refresh`를 호출해 얻는다

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| FE 주소를 `redirect_uri`로 (SPA 통상 방식) | OAuth Authorization Code Grant는 `redirect_uri`를 confidential client(백엔드)가 소유하는 것을 전제한다. FE를 경유시키면 **인가 코드가 브라우저 JS 컨텍스트에 노출**된다 |
| 콜백에서 access token을 쿼리로 전달 | access token이 URL·브라우저 히스토리·리퍼러·서버 로그에 남는다 |

## 결과 / 되돌리려면

세 가지가 따라온다:

1. **access token이 URL에 실리는 경로가 설계상 존재하지 않는다.** 로그인 직후와 새로고침 후의
   세션 획득 경로가 `/api/auth/refresh` 하나로 같아진다.
2. **인가 코드 유출 표면이 줄었다** — 카카오에서 BE로 직행한다.
3. **PKCE 도입 비용이 크게 낮아졌다** — FE 부담(Web Crypto·sessionStorage)이 사라져 BE 단독 구현이 된다
   ([ADR 0003](0003-no-pkce-yet.md)).

카카오 개발자 콘솔의 Redirect URI 등록이 **선행 조건**이 된다. 누락하면 카카오가 `KOE006`으로 거부한다.

# ADR 0003 — PKCE 미적용

- 상태: 보류 (도입 예정, 선행 검증 필요)
- 일자: 2026-08-25
- 관련: [ADR 0002](0002-backend-owns-redirect-uri.md), [roadmap.md](../../roadmap.md)

## 맥락

`state`만으로는 막히지 않는 공격이 있다 — **authorization code injection**이다.
공격자가 자기 브라우저로 흐름을 시작한 뒤, 피해자의 인가 코드를 자신의 콜백에 끼워 넣는다.

⚠️ **`state`로는 막히지 않는다.** 공격자 자신의 state는 유효하므로 검증을 통과한다.
`client_secret`도, `redirect_uri` 고정도 막지 못한다 — 공격자 대신 우리 서버가 정상 값을 써주기 때문이다.
이를 막는 것은 PKCE뿐이다.

## 결정

**지금은 `code_challenge`/`code_verifier`를 보내지 않는다.** 다만 이는 "불필요하다"가 아니라
**"검증 후 도입한다"**는 보류다.

## 배제한 대안과 근거

보류 사유는 **하나만 남았다**: 카카오 discovery 문서는 `code_challenge_methods_supported: ["S256"]`을
명시하지만, 한글 REST API 문서에는 기재가 없다. → **도입 전 authorize→token 교환을 수동으로 검증해야 한다.**

원래 있던 다른 보류 사유(FE 구현 부담)는 [ADR 0002](0002-backend-owns-redirect-uri.md)로 **사라졌다**.

## 결과 / 되돌리려면

**도입 비용이 이미 크게 낮아져 있다.** BE가 redirect를 소유하면서 FE 부담(Web Crypto·sessionStorage)이
없어져 BE 단독 구현이 되었고, verifier를 `oauth_state` 쿠키에 함께 보관하면 **새 부품도 필요 없다**.

완화 요인: [ADR 0002](0002-backend-owns-redirect-uri.md) 전환 후 인가 코드가 FE를 거치지 않아
(카카오 → BE 직행) 유출 표면 자체가 크게 줄었다.

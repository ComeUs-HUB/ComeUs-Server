# ADR 0005 — Refresh Token은 불투명 랜덤 문자열, DB엔 해시만

- 상태: 채택
- 일자: 2026-08-13
- 관련: [ADR 0004](0004-no-refresh-token-rotation.md), [ADR 0006](0006-cookie-samesite-and-path.md), [domains/auth.md](../domains/auth.md)

## 맥락

Access Token을 JWT로 발급하기로 한 뒤, Refresh Token도 JWT로 할지 정해야 했다.
JWT로 하면 서명만으로 유효성을 판정할 수 있어 DB 조회가 없다.

## 결정

**Refresh Token은 JWT가 아닌 불투명 랜덤 문자열이다.**

- `SecureRandom` 32바이트 → base64url 43자
- DB `refresh_tokens` 테이블에는 **SHA-256 해시(`CHAR(64)`)만** 저장한다. 원문은 `Set-Cookie`로만 나간다
- 유효성 = **row 존재 AND `expiresAt > now()`**
- `user 1 : token N` — 로그아웃이 해당 기기만 무효화하므로 멀티 디바이스 동시 로그인이 전제다. `@OneToOne` 금지

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| Refresh Token도 JWT | 서버가 **폐기할 수 없다.** 로그아웃·탈퇴가 즉시 반영되지 않는다. 어차피 폐기를 위해 DB를 둘 거라면 claim을 파싱할 이유가 없다 |
| DB에 원문 저장 | DB가 유출되면 그대로 세션 탈취다. 서버는 요청받은 원문을 해시해 조회하므로 원문을 보관할 필요가 없다 |
| row 존재만으로 유효 판정 | **만료 토큰이 영구히 유효해진다.** `expiresAt` 검사가 반드시 함께 있어야 한다 |

## 결과 / 되돌리려면

claim을 파싱하는 곳이 없으므로 **서명과 `exp`가 전부 미사용**이다.
`jwt.refresh-expiration`(7일)의 용도는 `RefreshToken.expiresAt` 계산 + 쿠키 `Max-Age`이지 JWT `exp`가 아니다.

만료된 row는 `expiresAt` 검사로 거절되므로 기능상 무해하다 — 정리 배치는 후속 과제다.
저장소를 Redis로 옮기더라도 "불투명 문자열 + 해시 저장" 구조는 그대로 유지된다.

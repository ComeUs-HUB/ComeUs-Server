# ADR 0004 — Refresh Token Rotation 미적용

- 상태: 보류
- 일자: 2026-08-24
- 관련: [ADR 0005](0005-opaque-refresh-token-hashed-in-db.md), [domains/auth.md](../domains/auth.md)

## 맥락

Refresh Token Rotation(재발급 때마다 refresh token을 새로 발급하고 옛것을 폐기)은 흔히 권장되는 방식이다.
탈취된 refresh token의 유효 창을 좁히는 것이 목적이다.

## 결정

**Rotation을 적용하지 않는다.** 재발급은 **신규 access token만** 내보내고 refresh 쿠키는 그대로 둔다.
그 결과 **재발급 응답에 `Set-Cookie`가 없다.**

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| Rotation만 도입 | **재사용 탐지와 함께 도입해야 실익이 있다.** 탐지가 없으면 "옛 토큰이 다시 쓰였다"는 신호를 아무도 읽지 않아, 탈취 대응이라는 목적을 달성하지 못한다 |
| Rotation + 재사용 탐지 | 탐지가 현재 범위 밖이다. 둘을 함께 넣으면 멀티탭 경합 대응이 반드시 따라온다(아래) |

단독 rotation은 **보안 이득 없이 멀티탭 경합 대응 복잡도만** 남긴다.

## 결과 / 되돌리려면

지금은 **여러 탭이 동시에 `/refresh`를 호출해도 모두 성공한다.** FE의 "refresh 단일 Promise 묶기"가
필수가 아니라 권장 사항인 이유다.

도입할 때는 **Rotation과 재사용 탐지를 함께** 넣고, 멀티탭 경합 대응을 반드시 동반한다:
기존 row를 즉시 삭제하지 말고 `expiresAt`을 `now()+30초`로 단축하는 **서버측 grace period** +
FE 단일 Promise 묶기. 로그아웃·탈퇴는 grace 없이 즉시 삭제한다.

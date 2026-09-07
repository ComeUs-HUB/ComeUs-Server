# 결정 기록 (ADR)

이 디렉터리는 **왜 그렇게 정했는가**를 기록한다. *무엇이 구현돼 있는가*는 [domains/](../domains/)와
[architecture.md](../architecture.md)가, *무엇이 끝났는가*는 [roadmap.md](../roadmap.md)가 소유한다.

## 규칙

- **한 번 쓴 ADR은 고치지 않는다.** 오타 수정 외에는 본문을 바꾸지 않는다.
- 결정이 뒤집히면 기존 파일의 `상태:`를 `대체됨 → ADR NNNN`으로만 바꾸고, **새 ADR을 추가**한다.
- 번호는 이어붙이기만 한다. 재사용·재정렬하지 않는다.
- 다른 문서는 결정 근거를 복사하지 않고 여기로 링크한다.

일반적인 모범 사례와 **다르게** 정한 것, 되돌리는 비용이 큰 것, 리뷰에서 이견이 있었던 것만 남긴다.
"당연한 선택"은 ADR로 만들지 않는다.

## 목록

| # | 결정 | 상태 |
|---|---|---|
| [0001](0001-kakao-only-oauth.md) | 카카오 OAuth 전용 로그인 — 이메일+비밀번호 없음 | 채택 |
| [0002](0002-backend-owns-redirect-uri.md) | `redirect_uri`는 백엔드가 소유한다 | 채택 |
| [0003](0003-no-pkce-yet.md) | PKCE 미적용 | 보류 |
| [0004](0004-no-refresh-token-rotation.md) | Refresh Token Rotation 미적용 | 보류 |
| [0005](0005-opaque-refresh-token-hashed-in-db.md) | Refresh Token은 불투명 랜덤 문자열, DB엔 해시만 | 채택 |
| [0006](0006-cookie-samesite-and-path.md) | 쿠키 2종의 `SameSite`·`Path`를 다르게 둔다 | 채택 |
| [0007](0007-single-entrypoint-for-401-403.md) | 401·403을 `JwtAuthenticationEntryPoint` 하나가 전담 | 채택 |
| [0008](0008-flyway-owns-schema.md) | 스키마 소유자는 Flyway (`ddl-auto: validate`) | 채택 |
| [0009](0009-signup-race-retry-at-login-boundary.md) | 가입 경합을 로그인 경계에서 1회 재시도로 흡수 | 채택 |

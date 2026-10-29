# 카카오 로그인 + silent refresh

- **답하는 질문**: 카카오 로그인부터 첫 API 호출까지 토큰이 어디서 만들어지고 어디에 실리는가
- **범위**: 인가 시작 → 콜백 → refresh 쿠키 발급 → access token 재발급 → 보호 API 호출. 로그아웃·탈퇴는 제외
- **원천**: [auth.md](../../domains/auth.md) · ADR [0002](../../decisions/auth/0002-backend-owns-redirect-uri.md) · [0004](../../decisions/auth/0004-no-refresh-token-rotation.md) · [0005](../../decisions/auth/0005-opaque-refresh-token-hashed-in-db.md) · [0006](../../decisions/auth/0006-cookie-samesite-and-path.md) · [0009](../../decisions/auth/0009-signup-race-retry-at-login-boundary.md)

![카카오 로그인 시퀀스](../images/seq-auth-login.png)

<details>
<summary>Mermaid 원본 — 내용은 이 블록이 기준이고 PNG는 여기서 다시 그린다</summary>

```mermaid
sequenceDiagram
    autonumber
    actor B as 브라우저
    participant AC as AuthController
    participant AS as AuthService
    participant K as KakaoOAuthClient
    participant Kakao as 카카오
    participant US as UserService
    participant DB as MySQL

    B->>AC: GET /api/auth/kakao/authorize
    AC->>AS: generateOAuthState()
    AC-->>B: 302 카카오 인가 URL<br/>Set-Cookie oauth_state (Lax)
    B->>Kakao: 로그인 · 동의
    Kakao-->>B: 302 → BE redirect_uri (/api/auth/kakao/callback)
    B->>AC: GET /kakao/callback?code&state (oauth_state 쿠키 동봉)
    Note over B,Kakao: redirect_uri는 BE 콜백 (ADR 0002) — 브라우저는 302를 따라갈 뿐, FE JS는 code를 보지 않는다
    alt state 불일치 · 누락
        AC-->>B: 302 FE /oauth/complete?error=A009
    end

    AC->>AS: kakaoLogin(code)
    rect rgba(128,128,128,0.12)
        Note over AS,Kakao: 트랜잭션 밖 (NOT_SUPPORTED) — 외부 왕복 동안 커넥션을 잡지 않는다
        AS->>K: requestToken(code)
        K->>Kakao: 토큰 교환
        AS->>K: fetchUserInfo(kakao access token)
        K->>Kakao: 사용자 정보
    end
    rect rgba(80,140,220,0.15)
        Note over AS,DB: 트랜잭션 (TransactionTemplate)
        AS->>AS: issueLoginSession(userInfo)
        AS->>US: findOrCreateKakaoUser(providerId, email, nickname)
        US->>DB: 조회 · 가입 · 탈퇴 회원 재활성화
        AS->>DB: RefreshToken 저장 (원문 아닌 SHA-256 해시)
    end
    Note over AS: 가입 경합으로 uk 위반이면 새 트랜잭션에서 1회 재시도
    AC-->>B: 302 FE /oauth/complete (신규면 ?isNewUser=true)<br/>Set-Cookie refresh_token (HttpOnly · Strict · Path=/api/auth)<br/>oauth_state 삭제

    B->>AC: POST /api/auth/refresh (refresh_token 쿠키)
    AC->>AS: reissue(raw)
    AS->>DB: 해시로 조회 · 만료 확인
    AC-->>B: 200 { accessToken } — 바디로만, 쿠키 재발급 없음

    B->>B: access token은 메모리에만 보관
    B->>AC: GET /api/users/me (Authorization: Bearer)
    Note over B,AC: 이후는 요청 처리 파이프라인 그대로
```

</details>

## 읽는 법

- **콜백은 어떤 실패에도 JSON을 내지 않는다** — 브라우저 주소창에 응답이 그대로 보이므로 모든 실패가 `?error=<코드>` 302다
- `oauth_state`는 `Lax`여야 한다. 카카오 → API 서버 이동이 cross-site 네비게이션이라 `Strict`이면 쿠키가 실리지 않는다
- `refresh_token`은 `Strict`라 CSRF 토큰 없이도 안전하고, 그 대가로 **FE·BE가 같은 상위 도메인에 있어야** 한다([deployment.md](../../deployment.md))
- 재발급은 access token만 새로 만든다. refresh token은 돌려쓰지 않는다(rotation 미적용, ADR 0004)

# ADR 0001 — 카카오 OAuth 전용 로그인

- 상태: 채택
- 일자: 2026-08-13
- 관련: [ADR 0002](0002-backend-owns-redirect-uri.md), [domains/auth.md](../domains/auth.md)

## 맥락

의류 판매 사이트의 회원 인증 수단을 정해야 했다. 일반적인 선택지는 이메일+비밀번호 자체 가입,
소셜 로그인, 또는 둘 다였다.

## 결정

**카카오 OAuth만 지원한다.** 이메일+비밀번호 가입·로그인을 만들지 않는다.

그 귀결로 다음을 **만들지 않는다**:

- `User.password` 컬럼
- `PasswordEncoder` Bean (BCrypt 등)
- 비밀번호 재설정·이메일 인증 플로우
- `INVALID_CREDENTIALS` 에러 코드 (옛 `U002`) — 도달 경로가 없어 삭제했다

`Provider` enum은 `KAKAO` 하나만 정의하되 enum으로 남긴다. 구글 등 provider 추가는 상수 추가로 확장된다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| 이메일+비밀번호 자체 가입 | 비밀번호 저장·재설정·이메일 인증까지 딸려 온다. 토이 프로젝트 범위에서 인증 자체가 목적이 아니다 |
| 카카오 + 자체 가입 병행 | 계정 통합(같은 이메일의 소셜/로컬 계정) 문제가 생긴다. 그 설계는 인증 하나에 쓰기엔 비용이 크다 |
| Spring Security OAuth2 Client 사용 | `starter-oauth2-client`는 표준 OIDC 흐름을 전제한다. 카카오는 표준에서 벗어난 부분이 있고, 흐름을 직접 다뤄야 학습 목적에도 맞는다 |

## 결과 / 되돌리려면

`email`이 **nullable**이다 — 카카오에서 이메일 동의를 거부할 수 있기 때문이다. 이 한 가지가
이후 설계 전반에 영향을 준다(유니크 제약, 가입 시 이메일 중복 처리 등).

LOCAL 로그인을 추가하려면: `V2__add_password.sql` + `PasswordEncoder` Bean + `Provider.LOCAL` +
이메일 중복 검사(`U001`) + `AuthErrorCode`에 `INVALID_CREDENTIALS` 재추가.

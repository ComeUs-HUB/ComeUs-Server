# ADR 0021 — `global/security`는 `UserRepository`를 직접 주입한다

- 상태: 채택
- 일자: 2026-10-29
- 관련: [architecture.md](../../architecture.md), [domains/auth.md](../../domains/auth.md), [ADR 0010](0010-access-denied-handler-for-authorization.md)

## 맥락

`CustomUserDetailsService`는 JWT subject로 회원을 읽어 `UserPrincipal`로 바꾼다.
그 조회 수단이 두 규칙 사이에서 어긋나 있었다.

- `UserRepository` Javadoc — "이 Repository는 user 패키지 안에서만 주입한다"
- [architecture.md](../../architecture.md) — `global/security` 전체는 도메인 타입을 직접 참조해도 된다.
  프레임워크가 `User`·`JwtProvider`·`AuthErrorCode`를 직접 요구하는 결합 지점이라 피할 수 없다

Phase 2 6단계에 쓴 주석은 이 충돌을 "결정 필요"로 남기고, **7단계에서 `UserService`에 조회 메서드를
만들 때 함께 결정**하기로 미뤘다. 근거로 인용한 `docs/SEQUENCE/phase2-auth.md`는 이후 문서 재편에서
사라져 지금은 확인할 수 없다.

Phase 4 2단계에서 `UserService.getUser(Long)`이 생겼다 — 장바구니가 쓴다.
미뤄 둔 조건이 충족됐으므로 지금 결정한다.

## 결정

**`CustomUserDetailsService`는 `UserRepository`를 계속 직접 주입한다.** `UserService`를 경유하지 않는다.

예외 정책이 서로 다르기 때문이다.

| 조회 수단 | 실패 시 던지는 것 | `JwtAuthenticationFilter`가 잡는가 |
|---|---|---|
| `UserRepository.findById` | 던지지 않는다 (`Optional`) | — |
| `UserService.getUser` | `BusinessException`(A001·A008) | **아니다** |
| `CustomUserDetailsService` | `UsernameNotFoundException`·`DisabledException` | 그렇다 → A001·A008 |

**필터의 catch 목록이 계약이다** — `ExpiredJwtException`·`JwtException`·`DisabledException`·
`UsernameNotFoundException` 넷뿐이다. `UserService.getUser`가 던지는 `BusinessException`은 여기 없고,
필터 단계라 `GlobalExceptionHandler`도 타지 않으므로 **그대로 새어 500이 된다.**

경유하려면 받은 `BusinessException`에서 `ErrorCode`를 읽어 A001 → `UsernameNotFoundException`,
A008 → `DisabledException`으로 되돌려야 한다. 같은 두 결과를 **에러코드 → 프레임워크 예외 → 에러코드**로
세 번 변환하는 셈이고, 한 쪽만 바뀌면 인증 실패가 조용히 500으로 나간다.

`Optional<User>`를 받아 **예외 종류를 호출자가 고르는** 형태가 이 지점에 맞다.
예외 정책이 도메인이 아니라 `UserDetailsService` 계약에 묶여 있다.

`UserRepository` 규칙과도 실제로는 충돌하지 않는다. 그 Javadoc이 밝힌 목적은
**회원 생성 규칙**을 user 안에 가두는 것이고, 여기는 생성이 아니라 조회다.

### 탈퇴 판정도 이 클래스가 한다

`UserPrincipal.isEnabled()`는 **아무도 호출하지 않는다.** 그 값을 검사하는 주체는
`DaoAuthenticationProvider`인데, `JwtAuthenticationFilter`는 provider를 거치지 않고
`loadUserByUsername`을 직접 부른 뒤 `SecurityContextHolder`를 채운다
(저장소에 `AuthenticationManager`·`DaoAuthenticationProvider` 사용처가 없다).

그래서 `WITHDRAWN` 판정은 `CustomUserDetailsService`가 직접 하고, `isEnabled()`는
`UserDetails` 계약을 채우는 값으로만 남는다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `UserService.getUser` 경유 + `BusinessException` 재변환 | 위의 3단 변환. 필터의 catch 목록과 user 도메인의 에러코드 정책이 서로 모르는 채 양쪽에서 바뀔 수 있어, 깨지는 방향이 **500**이다 |
| `UserService`에 `Optional<User>`를 돌려주는 전용 조회 메서드를 추가 | 방향이 거꾸로다. `global/security → domain`은 허용되지만, user 도메인이 Spring Security 계약에 맞춘 메서드를 품게 된다 — 조회 하나를 위해 도메인에 프레임워크 흔적을 남긴다 |
| 탈퇴 판정을 `UserPrincipal.isEnabled()`에 맡긴다 | 호출자가 없다. 그대로 두면 `WITHDRAWN_USER`(A008)가 **절대 반환되지 않는 죽은 코드**가 된다 |
| Security 배선 예외를 없애고 `global`의 모든 도메인 접근을 서비스 경유로 | `SecurityConfig`가 `JwtProvider`를, 필터가 `AuthErrorCode`를 직접 요구한다. 프레임워크 결합 지점이라 피할 수 없다 |

## 결과 / 되돌리려면

`global/` 안에서 `domain`을 import하는 곳은 `global/security/`와 `global/config/SecurityConfig`뿐이라는
규칙은 그대로 유효하고, [architecture.md](../../architecture.md)의 grep 한 줄로 판정된다.
이 ADR은 그 예외의 범위에 **Repository 주입이 포함된다**는 점을 확정할 뿐이다.

되돌리는 조건: `UserDetailsService` 구현을 버리고 `AuthenticationManager`·`DaoAuthenticationProvider`를
쓰는 배선으로 바꾸면 예외 변환을 provider가 맡게 되므로 이 결정의 근거가 사라진다. 그때 재검토한다.

검증: `JwtAuthenticationIntegrationTest` — 탈퇴 회원의 유효한 토큰 → 403 A008,
삭제된 회원의 서명 유효 토큰 → 401 A001. 두 케이스가 구분을 고정한다.

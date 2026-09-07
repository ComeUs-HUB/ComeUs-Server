# ADR 0007 — 401·403을 `JwtAuthenticationEntryPoint` 하나가 전담

- 상태: 채택
- 일자: 2026-08-20 (A008 분리로 확장: 2026-08-24)
- 관련: [domains/auth.md](../domains/auth.md), [conventions/api-response.md](../conventions/api-response.md)

## 맥락

Spring Security는 인증 실패를 `AuthenticationEntryPoint`가, 인가 실패(인증은 됐으나 권한 부족)를
`AccessDeniedHandler`가 처리하는 것을 전제한다. 통상 둘 다 등록한다.

## 결정

**`JwtAuthenticationEntryPoint` 하나가 401과 403을 모두 내보낸다. `AccessDeniedHandler`를 두지 않는다.**

`JwtAuthenticationFilter`는 토큰 검증에 실패해도 **short-circuit 하지 않는다.** request attribute에
사유만 남기고 체인을 계속 진행하며, EntryPoint가 그 attribute를 읽어 응답을 결정한다.

| 상황 | ErrorCode | HTTP |
|---|---|---|
| 토큰 없음 / 기타 | `UNAUTHORIZED` (A001) | 401 |
| 서명 위조·형식 오류 | `INVALID_TOKEN` (A003) | 401 |
| 만료 | `EXPIRED_TOKEN` (A004) | 401 |
| 탈퇴 회원 | `WITHDRAWN_USER` (A008) | **403** |

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| `AccessDeniedHandler`도 등록 | **도달 경로가 없다.** 필터가 예외 시 `SecurityContext`를 비운 채 체인을 통과시키므로 Spring Security는 이를 미인증(anonymous)으로 보고 **항상** `AuthenticationEntryPoint`를 호출한다. `AccessDeniedHandler`는 *인증은 됐는데 권한이 부족한* 경우에만 불린다 |
| 필터에서 즉시 응답 작성(short-circuit) | 응답 작성 지점이 두 곳으로 갈라진다. 한 곳에 모아야 포맷이 어긋나지 않는다 |

## 결과 / 되돌리려면

두 가지 함정이 있다:

1. ⚠️ **EntryPoint에서 HTTP 상태를 하드코딩하면 안 된다.** `errorCode.getHttpStatus()`를 써야 한다.
   `401`을 박아두면 A008(403)이 401로 뭉개진다.
2. ⚠️ **탈퇴 사유를 attribute로 넘기지 않으면 A008은 절대 반환되지 않는 죽은 코드가 된다**(전부 A001로 뭉개짐).
   `CustomUserDetailsService`가 `UsernameNotFoundException`(미존재)과 `DisabledException`(탈퇴)을
   **구분해서 던져야** 한다.

401/403 응답은 필터 단계라 `GlobalExceptionHandler`를 타지 않는다 → EntryPoint가 `ObjectMapper`로
[공통 응답 포맷](../conventions/api-response.md)의 JSON을 직접 작성한다.

ADMIN 권한 체크가 들어오면 `AccessDeniedHandler`의 도달 경로가 생기므로 **그 시점에 재검토**한다.

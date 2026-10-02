# ADR 0010 — 인가 실패(403)는 `JwtAccessDeniedHandler`가 맡는다

- 상태: 채택
- 일자: 2026-09-15
- 대체: [ADR 0007](0007-single-entrypoint-for-401-403.md)
- 관련: [architecture.md](../../architecture.md), [conventions/api-response.md](../../conventions/api-response.md)

## 맥락

[ADR 0007](0007-single-entrypoint-for-401-403.md)은 `JwtAuthenticationEntryPoint` 하나가 401과 403을
모두 내보내기로 했다. 그 근거는 **"`AccessDeniedHandler`는 도달 경로가 없다"**였고,
`SecurityConfig`가 `anyRequest().authenticated()`만 걸어 *로그인했는가*까지만 판정하던 동안에는
사실이었다 — 인가 단계에서 거절되는 요청 자체가 존재하지 않았다.

Phase 3에서 카테고리 쓰기에 `hasRole("ADMIN")`이 붙으면서 그 전제가 깨진다.
**인증은 됐으나 권한이 부족한** 요청이 처음 생긴다. 0007도 "ADMIN 권한 체크가 들어오면
`AccessDeniedHandler`의 도달 경로가 생기므로 그 시점에 재검토한다"고 예고해 두었다.

## 결정

**`JwtAccessDeniedHandler`를 등록한다.** 인가 실패는 `AuthErrorCode.FORBIDDEN`(A002)로
이 핸들러가 내보낸다. EntryPoint는 그대로 남고, 하는 일도 바뀌지 않는다.

| 상황 | 처리 주체 | ErrorCode | HTTP |
|---|---|---|---|
| 토큰 없음 / 위조 / 만료 / 탈퇴 | `JwtAuthenticationEntryPoint` | A001·A003·A004·A008 | 401 (A008만 403) |
| 인증된 USER가 ADMIN 전용 엔드포인트 호출 | **`JwtAccessDeniedHandler`** | **A002** | **403** |

두 핸들러는 같은 방식으로 응답을 쓴다 — 주입받은 `ObjectMapper`로 직접 직렬화하고,
`charset=UTF-8`을 명시하며, 상태 코드를 `errorCode.getHttpStatus()`에서 가져온다.
모양이 어긋나면 응답 작성 지점을 나눈 의미가 없어진다.

## 배제한 대안과 근거

| 대안 | 배제 이유 |
|---|---|
| EntryPoint가 403까지 계속 전담 | 인가 실패는 `AuthenticationException`이 아니라 `AccessDeniedException`이다. `ExceptionTranslationFilter`는 **인증된** 주체의 이 예외를 `AccessDeniedHandler`에게만 넘긴다 — EntryPoint로 끌어오려면 프레임워크 흐름을 거스르는 배선이 필요하다 |
| Spring Security 기본 핸들러에 맡긴다 | **바디 없는 403**이 나가 공통 `{success, data, error}` 포맷이 깨진다. 클라이언트가 `error.code`를 읽지 못하고, 401과 403의 응답 모양이 서로 달라진다 |
| `@PreAuthorize`로 서비스에서 판정 | 응답 작성 지점이 `GlobalExceptionHandler`로 옮겨갈 뿐, 권한 판정이 필터체인과 서비스 두 곳으로 갈라진다 |

## 결과 / 되돌리려면

**0007이 적은 것 중 아직 유효한 부분이 있다.** 익명 요청은 `AccessDeniedException`이어도
EntryPoint로 간다 — `JwtAuthenticationFilter`가 실패 시 `SecurityContext`를 비우므로
Spring Security가 이를 미인증으로 보기 때문이다. 그래서 **토큰 없이 ADMIN 경로를 호출하면
403이 아니라 401(A001)**이고, 이 핸들러를 등록해도 A001·A003·A004·A008의 동작은 바뀌지 않는다.

403을 내보내는 곳이 둘이 됐다 — A002(권한 부족)와 A008(탈퇴 회원). 같은 상태 코드를 두 곳이
내보내는 것은 의도된 것이고, 코드로 구분된다.

검증: `CategoryControllerTest`(USER → 403 A002, 익명 → 401 A001),
그리고 2026-09-15 MySQL 실기동 — DB에서 `role`만 ADMIN으로 바꾸자 **같은 토큰**으로 403이 201이 됐다.

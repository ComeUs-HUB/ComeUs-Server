package com.toyproject.backend.global.security;

/**
 * [6단계 · H] 인증 실패 응답 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * <b>인증 실패 응답을 전담하는 단 하나의 주체.</b> 401뿐 아니라 <b>403({@code WITHDRAWN_USER})도
 * 여기서 나간다.</b>
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code AuthenticationEntryPoint}를 구현한다.</li>
 *   <li>{@link JwtAuthenticationFilter}가 남긴 request attribute에서 {@code ErrorCode}를 읽는다.
 *       없으면 {@code UNAUTHORIZED}(A001)로 폴백한다(토큰 없이 보호 자원에 접근한 경우).</li>
 *   <li>필터 단계라 {@code GlobalExceptionHandler}를 타지 않는다. 공통 {@code ApiResponse} 포맷을
 *       {@code ObjectMapper}로 <b>직접 직렬화</b>해야 한다.</li>
 *   <li>{@code Content-Type: application/json} + {@code charset=UTF-8}을 명시한다(한글 메시지가 깨진다).</li>
 * </ul>
 *
 * <p><b>{@code AccessDeniedHandler}를 두지 않는 근거</b><br>
 * {@link JwtAuthenticationFilter}가 예외 시 {@code SecurityContext}를 비운 채 체인을 통과시키므로,
 * Spring Security는 이를 <b>미인증(anonymous)</b>으로 보고 항상 {@code AuthenticationEntryPoint}를 호출한다.
 * {@code AccessDeniedHandler}는 <i>인증은 됐는데 권한이 부족한</i> 경우에만 불리므로 현재 설계에는
 * 도달 경로가 없다. ADMIN 권한 체크가 들어오는 phase6에서 재검토한다.
 *
 * <p><b>주의</b><br>
 * ⚠️ HTTP 상태를 <b>하드코딩하지 말고 {@code errorCode.getHttpStatus()}를 쓴다.</b>
 * 이 EntryPoint는 403(A008)까지 함께 내리므로, {@code 401}을 박아두면 A008이 401로 뭉개져
 * "탈퇴 회원"과 "인증 필요"를 클라이언트가 구분할 수 없게 된다.
 */
public class JwtAuthenticationEntryPoint {
}

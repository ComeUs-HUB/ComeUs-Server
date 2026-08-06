package com.toyproject.backend.global.security;

/**
 * [6단계 · J] JWT 인증 필터 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code Authorization: Bearer} 헤더를 해석해 {@code SecurityContext}를 채운다.
 * Phase 2 인증 흐름에서 가장 함정이 많은 지점이다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code OncePerRequestFilter}를 확장하고, {@code UsernamePasswordAuthenticationFilter} 앞에 등록한다.</li>
 *   <li>Bearer 토큰이 <b>없으면 그냥 통과</b>시킨다. permitAll 경로(authorize·callback·refresh·logout)가
 *       있으므로 여기서 막으면 로그인 자체가 불가능해진다.</li>
 *   <li>토큰이 있으면 파싱 → {@code CustomUserDetailsService} 조회 → {@code SecurityContextHolder} 설정.</li>
 * </ul>
 *
 * <p><b>핵심 설계 — 예외 시 short-circuit 하지 않는다</b><br>
 * 실패해도 여기서 응답을 쓰지 않는다. {@code SecurityContext}를 비우고 <b>사유만 request attribute에 남긴 뒤
 * 체인을 계속 진행</b>한다. 그러면 Spring Security가 미인증(anonymous)으로 판단해
 * {@link JwtAuthenticationEntryPoint}를 호출하고, EntryPoint가 attribute를 읽어 상태 코드를 결정한다.
 * 응답 작성 주체를 한 곳으로 모으기 위한 구조다.
 *
 * <p><b>사유별 매핑</b>
 * <table border="1">
 *   <tr><th>상황</th><th>ErrorCode</th></tr>
 *   <tr><td>토큰 없음 / 사용자 미존재 등 기타</td><td>{@code UNAUTHORIZED}(U003, 401)</td></tr>
 *   <tr><td>서명 위조·형식 오류 ({@code JwtException})</td><td>{@code INVALID_TOKEN}(U005, 401)</td></tr>
 *   <tr><td>만료 ({@code ExpiredJwtException})</td><td>{@code EXPIRED_TOKEN}(U006, 401)</td></tr>
 *   <tr><td>탈퇴 회원 ({@code DisabledException})</td><td><b>{@code WITHDRAWN_USER}(U010, 403)</b></td></tr>
 * </table>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>{@code ExpiredJwtException}은 {@code JwtException}의 하위 타입이므로 <b>catch 순서가 중요하다.</b>
 *       {@code JwtException}을 먼저 잡으면 U006이 U005로 뭉개진다.</li>
 *   <li>탈퇴 사유를 attribute로 넘기지 않으면 U010은 절대 반환되지 않는다(전부 U003이 된다).</li>
 *   <li>attribute 키는 상수로 공개해 EntryPoint가 같은 키를 참조하게 한다.</li>
 * </ul>
 */
public class JwtAuthenticationFilter {
}

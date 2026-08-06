package com.toyproject.backend.global.security;

/**
 * [6단계 · J] 사용자 조회 서비스 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * JWT에서 꺼낸 userId로 실제 사용자를 조회해 {@link UserPrincipal}로 변환한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code UserDetailsService}를 구현한다. {@code loadUserByUsername}의 username 자리에는
 *       <b>userId(JWT subject)</b>가 들어온다.</li>
 *   <li><b>매 요청 DB를 조회한다.</b> JWT claim만 믿으면 탈퇴한 회원이 토큰 만료 전까지 계속 통과한다.
 *       캐싱하지 않는 것은 의도된 비용이다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>: {@code UserDetails loadUserByUsername(String userId)}
 *
 * <p><b>주의</b><br>
 * <b>거부 사유를 반드시 구분해서 던진다.</b>
 * <ul>
 *   <li>사용자 미존재 → {@code UsernameNotFoundException}</li>
 *   <li>{@code WITHDRAWN} → {@code DisabledException}</li>
 * </ul>
 * 둘을 같은 예외로 던지면 필터가 U010(403)과 U003(401)을 갈라낼 근거를 잃고,
 * {@code WITHDRAWN_USER}는 <b>절대 반환되지 않는 죽은 코드</b>가 된다.
 */
public class CustomUserDetailsService {
}

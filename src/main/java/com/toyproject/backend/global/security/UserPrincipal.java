package com.toyproject.backend.global.security;

/**
 * [6단계 · J] 인증 주체 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code SecurityContext}에 담기는 로그인 사용자 표현. 컨트롤러는
 * {@code @AuthenticationPrincipal UserPrincipal}로 <b>일관되게</b> 이 타입을 받는다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code UserDetails}를 구현한다.</li>
 *   <li>{@code User} 엔티티를 통째로 들고 있지 않는다. 요청 처리 중 detach된 엔티티를 참조하게 되고,
 *       필요 없는 필드까지 세션 수명 내내 붙들게 된다. <b>필요한 최소 정보만</b> 복사한다.</li>
 *   <li>{@code from(User)} 정적 팩터리로 변환 지점을 한 곳에 모은다.</li>
 * </ul>
 *
 * <p><b>예정 필드</b>: {@code id}, {@code role}, {@code status}
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code getAuthorities()} — {@code ROLE_} 접두사를 붙인 {@code SimpleGrantedAuthority} 1개</li>
 *   <li>{@code getUsername()} — userId 문자열. JWT subject와 대응된다</li>
 *   <li>{@code getPassword()} — 카카오 전용이라 비밀번호가 존재하지 않는다. null 반환</li>
 *   <li>{@code isEnabled()} — {@code status == ACTIVE}</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@code PasswordEncoder} Bean은 <b>만들지 않는다</b>(확정 사항). 비밀번호 개념 자체가 없다.
 */
public class UserPrincipal {
}

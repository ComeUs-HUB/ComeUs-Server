package com.toyproject.backend.domain.user.entity;

/**
 * [1단계 · G] 회원 상태
 *
 * <p><b>역할</b><br>
 * 소프트 삭제를 표현한다. 탈퇴해도 row가 남으므로 "살아 있는 회원"인지를 이 값으로 판정한다.
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>{@code CustomUserDetailsService}가 매 요청 이 값을 확인해 탈퇴 회원을 즉시 차단한다
 *       (JWT claim만 믿으면 탈퇴 후에도 토큰 만료 전까지 통과해 버린다).</li>
 *   <li>{@code WITHDRAWN} 회원이 동일 카카오 계정으로 재로그인하면 자동으로 {@code ACTIVE}로 되돌린다.</li>
 * </ul>
 */
public enum UserStatus {

    ACTIVE,
    WITHDRAWN
}

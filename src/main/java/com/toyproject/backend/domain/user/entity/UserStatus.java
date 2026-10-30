package com.toyproject.backend.domain.user.entity;

/**
 * [Phase 2 · 1단계] 소프트 삭제를 표현한다. 탈퇴해도 row가 남는다.
 *
 * <p>{@code CustomUserDetailsService}가 매 요청 확인해 탈퇴 회원을 즉시 막는다(ADR 0021).
 * <p>{@code WITHDRAWN} 회원이 같은 카카오 계정으로 재로그인하면 {@code ACTIVE}로 되돌아간다.
 */
public enum UserStatus {

    ACTIVE,
    WITHDRAWN
}

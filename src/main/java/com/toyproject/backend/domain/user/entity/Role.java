package com.toyproject.backend.domain.user.entity;

/**
 * [1단계 · G] 회원 권한 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 인가 판단의 근거. Access Token의 {@code role} claim으로 실리고,
 * {@code UserPrincipal}에서 {@code ROLE_} 접두사가 붙은 {@code GrantedAuthority}로 변환된다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>가입 시 항상 {@code USER}로 초기화한다.</li>
 *   <li><b>ADMIN 발급 플로우는 Phase 2 범위 밖</b>이다. 상수만 정의하고 승격 API는 만들지 않는다
 *       (발급 방식은 phase6-finalization R 항목에서 결정).</li>
 * </ul>
 *
 * <p><b>예정 상수</b>: {@code USER}, {@code ADMIN}
 */
public enum Role {
}

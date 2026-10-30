package com.toyproject.backend.domain.user.entity;

/**
 * [Phase 2 · 1단계] 인가 판단의 근거. 가입 시 항상 {@code USER}다.
 *
 * <p>access token의 {@code role} claim으로 실리지만 인가는 claim이 아니라 매 요청 DB를
 * 읽어 판정한다(ADR 0021). 승격 경로는 {@link User#promoteToAdmin()}뿐이고 API는 없다.
 */
public enum Role {

    USER,
    ADMIN
}

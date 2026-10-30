package com.toyproject.backend.domain.user.entity;

/**
 * [Phase 2 · 1단계] 소셜 로그인 제공자. {@code (provider, providerId)} 복합 유니크의 한 축이다.
 *
 * <p>사용처 없는 {@code LOCAL}을 미리 만들지 않는다 — 죽은 분기가 된다.
 */
public enum Provider {

    KAKAO
}

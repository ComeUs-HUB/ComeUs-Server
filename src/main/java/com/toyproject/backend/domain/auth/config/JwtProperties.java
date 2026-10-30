package com.toyproject.backend.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [Phase 2 · 5단계] {@code jwt.*} 설정 묶음. 서명·만료와 refresh 수명을 함께 담는다.
 *
 * <p>단위는 ms {@code long} 그대로 둔다. 초·{@code Duration} 변환은 쓰는 쪽이 한다.
 * <p>{@code @Validated}를 붙이지 않고 {@code secret} 길이는 {@code JwtProvider} 생성자가
 * 기동에서 거른다(ADR 0022).
 *
 * @param secret            HMAC 서명 키. 최소 32바이트이고 길이가 HS256/384/512를 결정한다 —
 *                          운영과 로컬의 길이가 다르면 서명 알고리즘도 달라진다
 * @param accessExpiration  access token 유효 기간 (ms)
 * @param refreshExpiration refresh token 유효 기간 (ms). JWT의 {@code exp}가 아니다
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        String secret,
        long accessExpiration,
        long refreshExpiration
) {
}

package com.toyproject.backend.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [Phase 2 · 4단계] {@code kakao.*} 설정 묶음. 어느 yml이 무엇을 갖는지는
 * {@code docs/domains/auth.md}의 "설정 값".
 *
 * <p>{@code @Validated}를 붙이지 않는다 — 누락된 값은 {@code null}로 바인딩된다(ADR 0022).
 * <p>{@code redirectUri}는 FE가 아니라 BE 콜백이다. 카카오 콘솔에 같은 값이 등록돼 있어야
 * 하고, 누락 시 카카오가 {@code KOE006}으로 거부한다(ADR 0002).
 *
 * @param clientSecret 로그에 남기지 않는다
 * @param adminKey     연결 해제에만 쓴다. 로그에 남기지 않는다
 * @param redirectUri  인가 요청과 토큰 교환에 같은 값을 보내야 카카오가 대조를 통과시킨다
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(

        String clientId,
        String clientSecret,
        String adminKey,
        String redirectUri,
        String authorizeUri,
        String tokenUri,
        String userInfoUri,
        String unlinkUri
) {
}

package com.toyproject.backend.domain.auth.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * [Phase 2 · 4단계] 카카오 토큰 교환({@code /oauth/token}) 응답.
 *
 * <p>여기 담긴 토큰은 카카오의 것이지 우리 서비스의 것이 아니다.
 * <p>미지의 필드는 Jackson이 무시하므로 카카오가 응답 필드를 추가해도 깨지지 않는다.
 *
 * @param accessToken  곧바로 {@code fetchUserInfo}에만 쓰고 저장하지 않는다
 * @param refreshToken 사용처가 없다 — 연결 해제는 Admin Key로 한다
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KakaoTokenResponse(

        String accessToken,
        String tokenType,
        String refreshToken,
        Integer expiresIn,
        String scope
) {
}

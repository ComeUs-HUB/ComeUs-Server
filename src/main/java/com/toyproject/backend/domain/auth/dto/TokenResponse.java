package com.toyproject.backend.domain.auth.dto;

/**
 * [Phase 2 · 7단계] {@code POST /api/auth/refresh}의 응답 바디.
 * FE는 이 값을 메모리에만 보관한다.
 *
 * <p>{@code refreshToken}을 담지 않는다. refresh token은 {@code HttpOnly} 쿠키로만 오가며,
 * 여기 넣는 순간 JS가 읽을 수 있어 {@code HttpOnly}로 얻은 방어가 통째로 무의미해진다.
 *
 * @param accessTokenExpiresIn 초 단위. {@code JwtProvider}가 유일한 변환 지점이라
 *                             여기서 다시 1000으로 나누면 만료가 1000배 짧아진다
 */
public record TokenResponse(String accessToken, String tokenType, long accessTokenExpiresIn) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(String accessToken, long accessTokenExpiresIn) {
        return new TokenResponse(accessToken, BEARER, accessTokenExpiresIn);
    }
}

package com.toyproject.backend.domain.auth.dto;

/**
 * [7단계 · I·K] 토큰 응답 DTO
 *
 * <p><b>역할</b><br>
 * {@code POST /api/auth/refresh}의 응답 바디. FE는 이 값을 <b>메모리(JS 변수)에만</b> 보관한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@code tokenType}은 항상 {@code "Bearer"}다. 호출부가 매번 문자열을 적지 않도록
 *       {@link #of(String, long)} 정적 팩터리가 채운다.</li>
 *   <li>문서상 8단계 파일이지만 {@code AuthService.reissue}의 반환 타입이라 7단계에서 확정한다.</li>
 * </ul>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li><b>{@code refreshToken}을 절대 포함하지 않는다.</b> refresh token은 오직 {@code HttpOnly}
 *       쿠키로만 오간다. 여기에 넣는 순간 JS가 읽을 수 있게 되어 {@code HttpOnly}로 얻은 방어가
 *       통째로 무의미해진다. ({@code localStorage}/{@code sessionStorage}에도 어떤 토큰도 저장하지 않는다.)</li>
 *   <li>⚠️ <b>{@code accessTokenExpiresIn}을 여기서 1000으로 나누지 않는다.</b> 설정값은 ms지만
 *       {@code JwtProvider.getAccessExpirationSeconds()}가 <b>유일한 변환 지점</b>으로 이미 초를 넘겨준다.
 *       한 번 더 나누면 만료가 1000배 짧아진다.</li>
 * </ul>
 *
 * @param accessToken          서명된 JWT
 * @param tokenType            항상 {@code "Bearer"}
 * @param accessTokenExpiresIn access token 유효 기간 (<b>초</b>)
 */
public record TokenResponse(String accessToken, String tokenType, long accessTokenExpiresIn) {

    private static final String BEARER = "Bearer";

    /**
     * @param accessTokenExpiresIn <b>초 단위</b> 값. {@code JwtProvider.getAccessExpirationSeconds()}를
     *                             그대로 넘긴다
     */
    public static TokenResponse of(String accessToken, long accessTokenExpiresIn) {
        return new TokenResponse(accessToken, BEARER, accessTokenExpiresIn);
    }
}

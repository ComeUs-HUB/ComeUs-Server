package com.toyproject.backend.domain.auth.token;

/**
 * [5단계 · I] Access Token 생성·파싱 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * jjwt 0.12.x로 access token을 서명·발급하고, 들어온 토큰을 검증해 claim을 꺼낸다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li><b>access token만 담당한다.</b> refresh token은 JWT가 아니므로 여기서 다루지 않는다
 *       (생성은 {@link SecureTokenGenerator}).</li>
 *   <li>키는 생성자에서 {@code Keys.hmacShaKeyFor(secret.getBytes(UTF_8))}로 한 번만 만들어 재사용한다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code String createAccessToken(User)} — subject=userId, claim {@code role}, 만료 30분</li>
 *   <li>{@code Claims parseClaims(String token)} — 서명 검증 후 payload 반환</li>
 *   <li>{@code long getAccessExpirationSeconds()} — 응답 바디의 {@code accessTokenExpiresIn}(초)</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * {@code parseClaims}는 예외를 <b>삼키거나 뭉개지 않고 그대로 던진다.</b>
 * {@code ExpiredJwtException}(만료)과 그 밖의 {@code JwtException}(서명 위조·형식 오류)을
 * 필터가 각각 A004/A003으로 구분해야 하는데, 여기서 {@code Optional}이나 boolean으로 뭉개면
 * 그 구분이 영영 불가능해진다.
 */
public class JwtProvider {
}

package com.toyproject.backend.domain.auth.config;

/**
 * [5단계 · I] JWT 설정 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code jwt.*} 설정 묶음. {@code JwtProvider}(서명·만료)와 {@code AuthService}·{@code CookieUtil}
 * (refresh 수명) 양쪽에서 참조한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code @ConfigurationProperties(prefix = "jwt")} record.</li>
 *   <li>세 곳 이상에서 쓰이므로 {@code @Value} 산발 주입 대신 타입으로 묶는다.</li>
 * </ul>
 *
 * <p><b>예정 컴포넌트</b>
 * <ul>
 *   <li>{@code secret} — 환경변수 {@code JWT_SECRET} 주입</li>
 *   <li>{@code accessExpiration} — 30분(ms)</li>
 *   <li>{@code refreshExpiration} — 7일(ms)</li>
 * </ul>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>HS256을 쓰므로 {@code secret}은 <b>최소 32바이트</b>여야 한다. 짧으면 키 생성 시점에 예외가 난다.</li>
 *   <li>{@code refreshExpiration}은 <b>JWT의 {@code exp}가 아니다.</b> refresh token은 JWT가 아니므로,
 *       이 값은 {@code RefreshToken.expiresAt} 계산과 쿠키 {@code Max-Age}에만 쓰인다.</li>
 * </ul>
 */
public record JwtProperties(/* 구현 단계에서 정의 */) {
}

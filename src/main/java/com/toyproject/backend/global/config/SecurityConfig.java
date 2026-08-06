package com.toyproject.backend.global.config;

/**
 * [6단계 · H] Spring Security 설정 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 필터 체인과 엔드포인트별 인증 정책을 정의한다. Spring Security 6.x의
 * {@code SecurityFilterChain} Bean 방식을 쓴다({@code WebSecurityConfigurerAdapter} 미사용).
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code csrf} / {@code formLogin} / {@code httpBasic} / {@code logout} 전부 disable,
 *       {@code sessionManagement(STATELESS)}</li>
 *   <li>{@code addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)}</li>
 *   <li>{@code exceptionHandling}에 {@link com.toyproject.backend.global.security.JwtAuthenticationEntryPoint}만
 *       등록한다({@code AccessDeniedHandler}는 두지 않는다 — 근거는 EntryPoint 문서 참고).</li>
 * </ul>
 *
 * <p><b>엔드포인트 정책</b>
 * <table border="1">
 *   <tr><th>엔드포인트</th><th>인증</th><th>이유</th></tr>
 *   <tr><td>{@code GET /api/auth/kakao/authorize}</td><td>permitAll</td><td>로그인 시작(브라우저 네비게이션)</td></tr>
 *   <tr><td>{@code GET /api/auth/kakao/callback}</td><td>permitAll</td><td>카카오가 리다이렉트하는 착지점</td></tr>
 *   <tr><td>{@code POST /api/auth/refresh}</td><td>permitAll</td><td>쿠키 자체가 자격증명</td></tr>
 *   <tr><td>{@code POST /api/auth/logout}</td><td>permitAll</td><td>access token 만료 상태에서도 로그아웃 가능해야 함</td></tr>
 *   <tr><td>{@code /api/users/me} (GET/PUT/DELETE)</td><td>authenticated</td><td></td></tr>
 *   <tr><td>그 외</td><td>{@code anyRequest().authenticated()}</td><td>공개 조회 API는 phase6에서 최종 점검</td></tr>
 * </table>
 *
 * <p><b>CORS</b>
 * <ul>
 *   <li>{@code allowCredentials(true)} <b>필수</b>(쿠키 왕복). origin은 {@code app.cors.allowed-origins} 명시 목록.</li>
 *   <li>CORS가 필요한 것은 {@code /refresh}·{@code /logout}·{@code /users/me} <b>뿐</b>이다.
 *       {@code /kakao/authorize}·{@code /kakao/callback}은 XHR이 아니라 브라우저 top-level 네비게이션이라
 *       CORS가 적용되지 않는다.</li>
 * </ul>
 *
 * <p><b>CSRF 토큰을 두지 않는 근거</b><br>
 * {@code SameSite=Strict}가 cross-site 요청에 쿠키를 붙이지 않아 원천 차단된다.
 * ⚠️ "FE/BE 동일 상위 도메인" 배포 전제가 깨지면 <b>CSRF 토큰 도입이 필수</b>가 된다.
 *
 * <p><b>예정 Bean</b>: {@code SecurityFilterChain}, {@code CorsConfigurationSource}
 */
public class SecurityConfig {
}

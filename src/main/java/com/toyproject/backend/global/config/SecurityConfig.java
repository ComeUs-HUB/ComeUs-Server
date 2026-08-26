package com.toyproject.backend.global.config;

import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.global.security.CustomUserDetailsService;
import com.toyproject.backend.global.security.JwtAuthenticationEntryPoint;
import com.toyproject.backend.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * [6단계 · H] Spring Security 설정
 *
 * <p><b>역할</b><br>
 * 필터 체인과 엔드포인트별 인증 정책을 정의한다. Spring Security 6.x의
 * {@code SecurityFilterChain} Bean 방식을 쓴다({@code WebSecurityConfigurerAdapter} 미사용).
 *
 * <table border="1">
 *   <caption>엔드포인트 정책</caption>
 *   <tr><th>엔드포인트</th><th>인증</th><th>이유</th></tr>
 *   <tr><td>{@code GET /api/auth/kakao/authorize}</td><td>permitAll</td><td>로그인 시작(브라우저 네비게이션)</td></tr>
 *   <tr><td>{@code GET /api/auth/kakao/callback}</td><td>permitAll</td><td>카카오가 리다이렉트하는 착지점</td></tr>
 *   <tr><td>{@code POST /api/auth/refresh}</td><td>permitAll</td><td>쿠키 자체가 자격증명</td></tr>
 *   <tr><td>{@code POST /api/auth/logout}</td><td>permitAll</td><td>access token 만료 상태에서도 로그아웃 가능해야 함</td></tr>
 *   <tr><td>{@code /api/users/me} (GET/PUT/DELETE)</td><td>authenticated</td><td>{@code anyRequest()}에 포함된다</td></tr>
 *   <tr><td>그 외</td><td>{@code anyRequest().authenticated()}</td><td>공개 조회 API는 phase6에서 최종 점검</td></tr>
 * </table>
 *
 * <p><b>CORS</b>
 * <ul>
 *   <li>{@code allowCredentials(true)} <b>필수</b>(쿠키 왕복). origin은 {@code app.cors.allowed-origins} 명시 목록.
 *       와일드카드({@code *})는 credentials와 함께 쓸 수 없다.</li>
 *   <li>CORS가 필요한 것은 {@code /refresh}·{@code /logout}·{@code /users/me} <b>뿐</b>이다.
 *       {@code /kakao/authorize}·{@code /kakao/callback}은 XHR이 아니라 브라우저 top-level 네비게이션이라
 *       CORS가 적용되지 않는다. 경로를 나눠봐야 이득이 없어 {@code /api/**}에 일괄 적용한다.</li>
 * </ul>
 *
 * <p><b>CSRF 토큰을 두지 않는 근거</b><br>
 * {@code SameSite=Strict}가 cross-site 요청에 쿠키를 붙이지 않아 원천 차단된다.
 * ⚠️ "FE/BE 동일 상위 도메인" 배포 전제가 깨지면 <b>CSRF 토큰 도입이 필수</b>가 된다.
 *
 * <p><b>주의</b><br>
 * {@link JwtAuthenticationFilter}를 Bean으로 두지 않고 여기서 {@code new}로 만든다 —
 * 근거는 해당 클래스 Javadoc(서블릿 체인 중복 등록) 참고.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PERMIT_ALL_GET = {
            "/api/auth/kakao/authorize",
            "/api/auth/kakao/callback"
    };

    private static final String[] PERMIT_ALL_POST = {
            "/api/auth/refresh",
            "/api/auth/logout"
    };

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final AppProperties appProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // AccessDeniedHandler는 등록하지 않는다 — 도달 경로가 없다(EntryPoint Javadoc 참고).
                .exceptionHandling(handling -> handling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PERMIT_ALL_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PERMIT_ALL_POST).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 인증 필터. <b>Bean으로 노출하지 않는다</b>(서블릿 필터 체인 중복 등록 방지).
     *
     * <p>{@code private}로 두어 Spring이 이 메서드를 Bean 정의로 오인하지 않게 한다.
     */
    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider, userDetailsService);
    }

    /**
     * CORS 설정.
     *
     * <p>{@code app.cors.allowed-origins}가 비어 있으면 <b>모든 cross-origin 요청이 막힌다</b>
     * (fail-safe). 설정 누락이 조용한 허용으로 이어지지 않게 하기 위함이다 —
     * {@link AppProperties}의 "결정 필요" 참고.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

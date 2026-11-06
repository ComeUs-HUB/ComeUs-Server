package com.toyproject.backend.global.config;

import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.user.entity.Role;
import com.toyproject.backend.global.security.CustomUserDetailsService;
import com.toyproject.backend.global.security.JwtAccessDeniedHandler;
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
 * [Phase 2 · 6단계] 필터 체인과 엔드포인트별 인증 정책. 정책은 아래 {@code requestMatchers}
 * 체인이, 엔드포인트 계약은 {@code docs/domains/}가 소유한다.
 *
 * <p>CORS는 쿠키 왕복 때문에 {@code allowCredentials(true)}가 필수라 origin 와일드카드를
 * 쓸 수 없다. 목록이 비면 모든 cross-origin이 차단된다(ADR 0022).
 * <p>CSRF 토큰을 두지 않는다 — {@code SameSite=Strict}가 cross-site 요청에 쿠키를 붙이지
 * 않는다. 동일 상위 도메인 배포 전제가 깨지면 도입이 필수다(ADR 0006).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String CATEGORIES_PATH = "/api/categories";

    /**
     * 카테고리 단건 경로. {@code {id}} 자리를 한 세그먼트만 받는 {@code *}로 쓴다 —
     * {@code /**}는 하위 경로까지 삼켜 나중에 추가되는 엔드포인트의 정책을 미리 정해버린다.
     */
    private static final String CATEGORIES_ITEM_PATH = CATEGORIES_PATH + "/*";

    private static final String PRODUCTS_PATH = "/api/products";

    /** 상품 단건 경로. {@code /*}를 쓰는 이유는 {@link #CATEGORIES_ITEM_PATH}와 같다. */
    private static final String PRODUCTS_ITEM_PATH = PRODUCTS_PATH + "/*";

    private static final String[] PERMIT_ALL_GET = {
            "/api/auth/kakao/authorize",
            "/api/auth/kakao/callback",
            // 트리 조회 하나만 연다. 하위 경로를 열지 않으므로 여기에 /** 를 붙이지 않는다 —
            // 카테고리에는 단건 조회 API가 없고, 붙이면 나중에 생기는 경로까지 조용히 열린다.
            CATEGORIES_PATH,
            // 상품은 단건 조회가 있어 두 줄이다. 목록과 /{id}만 열리고 그 아래는 열리지 않는다.
            PRODUCTS_PATH,
            PRODUCTS_ITEM_PATH
    };

    private static final String[] PERMIT_ALL_POST = {
            "/api/auth/refresh",
            "/api/auth/logout"
    };

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
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
                // 401은 EntryPoint, 403(권한 부족)은 AccessDeniedHandler가 나눠 맡는다.
                // 익명 요청은 AccessDeniedException이어도 EntryPoint로 가므로 기존 동작은 그대로다
                // (JwtAccessDeniedHandler Javadoc 참고).
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PERMIT_ALL_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PERMIT_ALL_POST).permitAll()
                        // 카테고리 쓰기는 ADMIN만. hasRole 은 ROLE_ 접두사를 스스로 붙이므로
                        // 여기에 Role.ADMIN.name() 을 그대로 넘긴다 — 접두사는 UserPrincipal 이 담당한다.
                        // "ADMIN" 리터럴이 아닌 name() 인 이유: 상수 이름이 바뀌면 인가가 런타임에
                        // 조용히 실패하는 대신 컴파일에서 걸린다.
                        .requestMatchers(HttpMethod.POST, CATEGORIES_PATH).hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, CATEGORIES_ITEM_PATH).hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, CATEGORIES_ITEM_PATH).hasRole(Role.ADMIN.name())
                        // 상품 쓰기도 같은 배선이다. POST만 컬렉션 경로이고 PUT·DELETE는 단건이다.
                        .requestMatchers(HttpMethod.POST, PRODUCTS_PATH).hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, PRODUCTS_ITEM_PATH).hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, PRODUCTS_ITEM_PATH).hasRole(Role.ADMIN.name())
                        // 공개 경로와 달리 /** 를 쓴다. 나중에 생기는 관리 경로도 ADMIN만 호출한다.
                        .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Bean으로 노출하지 않는다. {@code private}라 Spring이 Bean 정의로 오인하지 않는다. */
    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider, userDetailsService);
    }

    /** 목록이 비면 모든 cross-origin이 막힌다 — 누락이 조용한 허용이 되지 않는다(ADR 0022). */
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

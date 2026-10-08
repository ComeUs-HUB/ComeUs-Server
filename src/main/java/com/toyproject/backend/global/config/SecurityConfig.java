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
 *   <tr><td>{@code GET /api/categories}</td><td>permitAll</td><td>비회원에게 열린 <b>첫</b> 조회 API</td></tr>
 *   <tr><td>{@code /api/categories} POST·PUT·DELETE</td><td>{@code hasRole("ADMIN")}</td>
 *       <td>ADMIN 권한을 요구하는 <b>첫</b> 엔드포인트</td></tr>
 *   <tr><td>{@code GET /api/products}·{@code /api/products/{id}}</td><td>permitAll</td>
 *       <td>비회원 상품 탐색 — 목록과 단건 둘 다 연다</td></tr>
 *   <tr><td>{@code /api/products} POST·PUT·DELETE</td><td>{@code hasRole("ADMIN")}</td>
 *       <td>카테고리와 같은 배선을 그대로 쓴다</td></tr>
 *   <tr><td>{@code /api/users/me} (GET/PUT/DELETE)</td><td>authenticated</td><td>{@code anyRequest()}에 포함된다</td></tr>
 *   <tr><td>{@code /api/cart}·{@code /api/cart/items/**}</td><td>authenticated</td><td>{@code anyRequest()}에 포함된다(Phase 4)</td></tr>
 *   <tr><td>그 외</td><td>{@code anyRequest().authenticated()}</td><td>남은 공개 조회 API는 phase6에서 최종 점검</td></tr>
 * </table>
 *
 * <p><b>Phase 3에서 정책이 두 갈래 늘어났다</b>
 * <ul>
 *   <li><b>permitAll 조회</b>가 처음 생겼다. Phase 2까지 {@code permitAll}은 로그인 왕복 경로뿐이었고
 *       나머지는 전부 {@code anyRequest().authenticated()}에 걸려 있었다.
 *       카테고리 트리와 상품 조회는 비회원 탐색 경로라 열어야 한다([domains/catalog/]).</li>
 *   <li><b>인가 판정</b>이 처음 생겼다. {@code hasRole("ADMIN")}이 붙는 순간
 *       "인증은 됐으나 권한이 부족한" 요청이 실제로 발생하고, 그 응답은
 *       {@link JwtAccessDeniedHandler}가 A002(403)로 내보낸다 — 그 클래스가 예고한 도달 경로가 여기다.</li>
 * </ul>
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

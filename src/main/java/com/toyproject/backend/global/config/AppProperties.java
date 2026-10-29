package com.toyproject.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * [Phase 2 · 6단계] FE 복귀 주소와 CORS 허용 origin. {@code SecurityConfig}와
 * {@code AuthController}가 함께 쓰므로 auth가 아니라 {@code global/config}에 둔다.
 *
 * <p>{@code @Validated}를 붙이지 않고 compact 생성자가 {@code null}을 흡수한다 —
 * {@code app.*}가 통째로 없어도 {@code SecurityConfig} 기동 중 NPE가 나지 않는다.
 * <p>{@code frontend.base-url}만 fail-fast다. 가르는 근거는 ADR 0022.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        Frontend frontend,
        Cors cors,
        Cookie cookie
) {

    public AppProperties {
        frontend = (frontend != null) ? frontend : new Frontend(null);
        cors = (cors != null) ? cors : new Cors(null);
        cookie = (cookie != null) ? cookie : new Cookie(null);

        // 없으면 FE 복귀 URL이 "null/oauth/complete"가 되어 조용히 오동작한다(ADR 0022)
        if (!StringUtils.hasText(frontend.baseUrl())) {
            throw new IllegalArgumentException("app.frontend.base-url은 반드시 설정해야 합니다.");
        }
    }

    /**
     * @param baseUrl FE 복귀 주소. {@code {baseUrl}/oauth/complete}로 조립된다.
     *                비어 있으면 기동에서 거부한다(ADR 0022)
     */
    public record Frontend(String baseUrl) {
    }

    /**
     * @param allowedOrigins 허용 origin 명시 목록. 와일드카드는 {@code allowCredentials}와
     *                       함께 쓸 수 없다
     */
    public record Cors(List<String> allowedOrigins) {

        public Cors {
            allowedOrigins = (allowedOrigins != null) ? List.copyOf(allowedOrigins) : List.of();
        }
    }

    /**
     * 인증 쿠키 속성. 환경별로 나눈 근거는 ADR 0006, 기본값이 {@code true}인 근거는 ADR 0022.
     *
     * <p>래퍼 {@code Boolean}을 쓴다 — {@code boolean}이면 {@code app.cookie}만 있고
     * {@code secure}가 없을 때 조용히 {@code false}로 바인딩된다.
     *
     * @param secure 미설정 시 {@code true}
     */
    public record Cookie(Boolean secure) {

        public Cookie {
            secure = (secure == null) || secure;
        }
    }
}

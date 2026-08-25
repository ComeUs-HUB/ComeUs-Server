package com.toyproject.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * [6단계 · H] 앱 환경 설정
 *
 * <p><b>역할</b><br>
 * FE 복귀 주소와 CORS 허용 origin을 묶는다. 배포 환경마다 달라지는 값이라 코드에 박지 않는다.
 * {@code SecurityConfig}(CORS)와 {@code AuthController}(FE 복귀 주소)가 함께 쓰므로
 * auth 도메인이 아니라 {@code global/config}에 둔다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>중첩 record로 그룹을 나눈다. 프로파일별 yml(gitignore 대상)에서 주입한다.</li>
 *   <li>{@code KakaoProperties}와 같은 이유로 {@code @Validated}를 붙이지 않는다
 *       — 테스트 yml이 main을 shadow하므로 검증을 걸면 컨텍스트가 깨진다.</li>
 *   <li>대신 <b>compact 생성자에서 null을 흡수</b>한다. {@code app.*}가 통째로 없는 환경에서도
 *       중첩 record가 {@code null}로 바인딩돼 {@code SecurityConfig} 기동 중 NPE가 나는 것을 막는다.
 *       CORS는 "허용 목록 없음"으로 안전하게 닫힌다(fail-safe).</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 쿠키 왕복 때문에 {@code allowCredentials(true)}가 필수인데, 그러면 CORS 명세상
 * origin 와일드카드({@code *})를 쓸 수 없다. 반드시 명시 목록이어야 한다.
 *
 * <p><b>⚠️ 결정 필요 — 설정 누락을 어디서 잡을 것인가</b><br>
 * 지금은 누락 시 조용히 "빈 허용 목록"이 되어 <b>운영에서 CORS가 전부 막히는 형태로만</b> 드러난다.
 * {@code JwtProperties}(secret 길이)와 같은 고민이며, 두 곳의 방침을 함께 정하는 편이 좋다.
 * 대안: prod 프로파일에서만 {@code @Validated}를 켜거나, 기동 시 경고 로그를 남긴다.
 *
 * @param frontend 로그인 완료 후 302로 복귀할 FE 주소 묶음 (8단계에서 사용)
 * @param cors     CORS 허용 origin 묶음
 * @param cookie   인증 쿠키 속성 묶음 (8단계에서 사용)
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
    }

    /**
     * @param baseUrl {@code AuthController}가 {@code {baseUrl}/oauth/complete}를 조립한다 (local: {@code http://localhost:3000})
     */
    public record Frontend(String baseUrl) {
    }

    /**
     * @param allowedOrigins 허용 origin <b>명시 목록</b>. 와일드카드를 쓸 수 없다(위 주의 참고)
     */
    public record Cors(List<String> allowedOrigins) {

        public Cors {
            allowedOrigins = (allowedOrigins != null) ? List.copyOf(allowedOrigins) : List.of();
        }
    }

    /**
     * 인증 쿠키 속성. {@code CookieUtil}이 참조한다.
     *
     * <p><b>{@code secure}를 환경별로 나눈 이유</b><br>
     * Chrome·Firefox는 {@code http://localhost}를 "신뢰할 수 있는 출처"로 보고 평문 HTTP에서도
     * {@code Secure} 쿠키를 저장하지만, <b>WebKit(Safari)은 그 예외를 두지 않아 거부한다.</b>
     * 그러면 {@code oauth_state}가 저장되지 않아 콜백이 전부 A009로 실패한다 —
     * 로컬 개발이 브라우저에 따라 불가능해진다.
     *
     * <p>⚠️ <b>기본값은 {@code true}다.</b> 설정을 빠뜨렸을 때 {@code Secure}가 꺼지는 쪽으로
     * 실패하면 안 된다. 끄는 것은 로컬 프로파일에서 <b>명시적으로</b> 선언해야 한다.
     * {@code Boolean}(래퍼)을 쓰는 이유도 같다 — {@code boolean}이면 {@code app.cookie}만 있고
     * {@code secure}가 없을 때 조용히 {@code false}로 바인딩된다.
     *
     * @param secure 쿠키에 {@code Secure} 속성을 붙일지. 미설정 시 {@code true}
     */
    public record Cookie(Boolean secure) {

        public Cookie {
            secure = (secure == null) || secure;
        }
    }
}

package com.toyproject.backend.domain.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * [4단계 · G] 카카오 호출용 RestClient 설정
 *
 * <p><b>역할</b><br>
 * {@code KakaoOAuthClient}가 쓸 HTTP 클라이언트 Bean을 제공한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li><b>신규 의존성이 없다.</b> {@code RestClient}는 {@code spring-boot-starter-web}에 포함돼 있다.
 *       {@code starter-oauth2-client}·webflux는 도입하지 않는다(카카오 연동만을 위해 인증 스택 전체를
 *       끌어오면 우리가 확정한 쿠키·리다이렉트 설계와 충돌한다).</li>
 *   <li>{@code KakaoProperties}가 <b>절대 URI</b>를 주므로 {@code baseUrl}을 설정하지 않는다.</li>
 *   <li>요청 팩터리는 spring-web의 {@code SimpleClientHttpRequestFactory}를 직접 쓴다.
 *       {@code ClientHttpRequestFactories}는 폐기 예정이라 Boot 버전에 따라 API가 흔들린다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 같은 타입 Bean이 2개라 주입 지점에서 {@code @Qualifier}가 필요하다
 * ({@code KakaoOAuthClient} 생성자 참고).
 */
@Configuration
public class RestClientConfig {

    /** 연결 수립까지 허용하는 시간. 카카오가 응답하지 않을 때 우리 스레드가 물리는 시간을 제한한다. */
    private static final int CONNECT_TIMEOUT_MS = 3_000;

    /** 응답 수신까지 허용하는 시간. 미설정 시 카카오 지연이 그대로 우리 스레드 점유로 이어진다. */
    private static final int READ_TIMEOUT_MS = 5_000;

    /**
     * 토큰 교환용 클라이언트({@code kauth.kakao.com}).
     *
     * <p>{@code KakaoOAuthClient.requestToken}이 쓴다.
     *
     * @param builder Boot가 제공하는 prototype 스코프 빌더라 Bean마다 새 인스턴스를 받는다
     */
    @Bean
    public RestClient kauthRestClient(RestClient.Builder builder) {
        return builder.requestFactory(timeoutRequestFactory()).build();
    }

    /**
     * 사용자정보·연결해제용 클라이언트({@code kapi.kakao.com}).
     *
     * <p>{@code KakaoOAuthClient.fetchUserInfo}·{@code unlink}가 쓴다.
     * kauth와 호스트가 다를 뿐 설정은 같지만, 한쪽 타임아웃만 조정해야 할 때를 위해 Bean을 나눠 둔다.
     */
    @Bean
    public RestClient kapiRestClient(RestClient.Builder builder) {
        return builder.requestFactory(timeoutRequestFactory()).build();
    }

    /** 타임아웃이 걸린 요청 팩터리를 만든다. 두 Bean이 각자의 인스턴스를 갖도록 매번 새로 생성한다. */
    private ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }
}

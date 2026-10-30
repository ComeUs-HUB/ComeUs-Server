package com.toyproject.backend.domain.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * [Phase 2 · 4단계] {@code KakaoOAuthClient}가 쓸 {@code RestClient} 두 개.
 *
 * <p>{@code starter-oauth2-client}를 도입하지 않는다(ADR 0001). {@code RestClient}는
 * {@code starter-web}에 이미 있어 신규 의존성이 없다.
 * <p>요청 팩터리로 {@code SimpleClientHttpRequestFactory}를 직접 쓴다 —
 * {@code ClientHttpRequestFactories}는 폐기 예정이라 Boot 버전마다 API가 흔들린다.
 * <p>같은 타입 Bean이 둘이라 주입 지점에 {@code @Qualifier}가 필요하다.
 */
@Configuration
public class RestClientConfig {

    /** 연결 수립까지 허용하는 시간. 카카오가 응답하지 않을 때 우리 스레드가 물리는 시간을 제한한다. */
    private static final int CONNECT_TIMEOUT_MS = 3_000;

    /** 응답 수신까지 허용하는 시간. 미설정 시 카카오 지연이 그대로 우리 스레드 점유로 이어진다. */
    private static final int READ_TIMEOUT_MS = 5_000;

    /** 토큰 교환용({@code kauth.kakao.com}). {@code builder}는 prototype이라 Bean마다 새 것이다. */
    @Bean
    public RestClient kauthRestClient(RestClient.Builder builder) {
        return builder.requestFactory(timeoutRequestFactory()).build();
    }

    /** 사용자정보·연결해제용({@code kapi.kakao.com}). 한쪽 타임아웃만 조정할 수 있게 나눠 둔다. */
    @Bean
    public RestClient kapiRestClient(RestClient.Builder builder) {
        return builder.requestFactory(timeoutRequestFactory()).build();
    }

    /** 두 Bean이 각자의 인스턴스를 갖도록 매번 새로 만든다. */
    private ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }
}

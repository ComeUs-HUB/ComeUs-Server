package com.toyproject.backend.domain.payment.gateway.toss;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * [Phase 4 · 9단계] 토스 호출용 {@code RestClient} 두 개. 읽기 타임아웃만 다르다.
 *
 * <p>승인·취소는 돈이 움직여 짧게 끊으면 결과 불명이 늘고, 조회는 실패해도 다음 주기에 다시 하면 된다(ADR 0030).
 */
@Configuration
public class TossRestClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int PAYMENT_READ_TIMEOUT_MS = 30_000;
    private static final int LOOKUP_READ_TIMEOUT_MS = 5_000;

    /** 승인·취소용. */
    @Bean
    public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
        return build(builder, properties, PAYMENT_READ_TIMEOUT_MS);
    }

    /** 조회용. 복구 배치가 한 건에 오래 묶이지 않게 한다. */
    @Bean
    public RestClient tossLookupRestClient(RestClient.Builder builder, TossProperties properties) {
        return build(builder, properties, LOOKUP_READ_TIMEOUT_MS);
    }

    private static RestClient build(RestClient.Builder builder, TossProperties properties, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(readTimeoutMs);
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.secretKey()))
                .requestFactory(factory)
                .build();
    }

    /** 토스 규칙: 시크릿 키 뒤에 콜론을 붙여 Base64로 인코딩한다. */
    static String basicAuth(String secretKey) {
        String raw = (secretKey == null ? "" : secretKey) + ":";
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}

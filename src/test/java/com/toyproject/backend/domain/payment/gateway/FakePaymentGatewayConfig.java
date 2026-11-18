package com.toyproject.backend.domain.payment.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** [Phase 4 · 10단계] 토스 구현 대신 {@link FakePaymentGateway}를 주입한다. 같은 설정을 쓰는 테스트끼리 컨텍스트를 공유한다. */
@TestConfiguration
public class FakePaymentGatewayConfig {

    @Bean
    @Primary
    public FakePaymentGateway fakePaymentGateway() {
        return new FakePaymentGateway();
    }
}

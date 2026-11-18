package com.toyproject.backend.domain.payment.gateway.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [Phase 4 · 9단계] {@code toss.*} 설정.
 *
 * @param secretKey 환경변수 {@code TOSS_SECRET_KEY}로만 받는다. 로그에 남기지 않는다
 */
@ConfigurationProperties(prefix = "toss")
public record TossProperties(String baseUrl, String secretKey) {
}

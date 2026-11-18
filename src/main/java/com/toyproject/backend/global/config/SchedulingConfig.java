package com.toyproject.backend.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켠다. 인스턴스마다 돌므로 작업은 겹쳐 실행돼도 안전해야 한다.
 *
 * <p>테스트는 {@code app.scheduling.enabled=false}로 끈다. 스케줄러 메서드를 직접 불러 검증하고,
 * 1분 주기 실행이 다른 테스트의 데이터를 건드리지 않게 한다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

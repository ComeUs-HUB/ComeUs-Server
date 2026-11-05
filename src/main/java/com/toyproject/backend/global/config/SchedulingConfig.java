package com.toyproject.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 작업을 켠다. 인스턴스마다 돌므로 작업은 겹쳐 실행돼도 안전해야 한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

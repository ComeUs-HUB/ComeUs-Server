package com.toyproject.backend.global.config;

import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

@Configuration
@EnableJpaAuditing
public class JpaConfig {

    /** 격리 수준이 다른 트랜잭션에 합류하면 조용히 무시하지 않고 예외를 던진다(ADR 0025). */
    @Bean
    public TransactionManagerCustomizer<AbstractPlatformTransactionManager> validateExistingTransaction() {
        return transactionManager -> transactionManager.setValidateExistingTransaction(true);
    }
}

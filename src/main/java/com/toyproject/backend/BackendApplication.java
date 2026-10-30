package com.toyproject.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan} — {@code @ConfigurationProperties} 클래스가
 * {@code global/config}와 {@code domain/auth/config}에 나뉘어 있어 전체를 스캔한다.
 * 클래스별 {@code @EnableConfigurationProperties} 등록이 필요 없다.
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}

package com.rizenfood.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 라이즌푸드 API 진입점.
 *
 * 스케줄링: 방치된 미결제 주문 정리(OrderService.expireStalePending) 등.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}

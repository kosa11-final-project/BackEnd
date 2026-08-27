package com.stockit.backend.feature.strategy.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 단일 Backend 인스턴스의 AI 전략 SSE 연결과 heartbeat 설정 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        AiStrategySseProperties.class,
        StrategyNotificationRecoveryProperties.class
})
public class AiStrategySseConfiguration {
}

package com.stockit.backend.feature.statistics.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.strategy-statistics.schedule", name = "enabled", havingValue = "true")
public class StrategyStatisticsSchedulingConfiguration {
}

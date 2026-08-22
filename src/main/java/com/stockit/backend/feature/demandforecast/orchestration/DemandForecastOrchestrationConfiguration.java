package com.stockit.backend.feature.demandforecast.orchestration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DemandForecastOrchestrationProperties.class)
public class DemandForecastOrchestrationConfiguration {
    @Bean
    @ConditionalOnProperty(
            prefix = "app.demand-forecast.orchestration",
            name = "enabled",
            havingValue = "true"
    )
    public DemandForecastFastApiClient demandForecastFastApiClient(
            RestClient.Builder builder,
            DemandForecastOrchestrationProperties properties
    ) {
        return new DemandForecastFastApiClient(builder, properties);
    }
}

package com.stockit.backend.feature.strategy.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 전략 생성 작업의 Main, Retry, Dead Letter Queue 흐름 구성
 *
 * <p>일시 오류는 Retry Queue의 TTL 이후 Main Queue로 되돌리고, 영구 오류와
 * 재시도 소진 작업은 DLQ에 격리해 자동 재처리 대상과 운영 확인 대상을 분리</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StrategyGenerationMessagingProperties.class)
@ConditionalOnProperty(
        prefix = "app.ai-strategy.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StrategyGenerationRabbitConfiguration {

    @Bean
    DirectExchange strategyGenerationExchange() {
        return new DirectExchange(
                StrategyGenerationMessagingProperties.MAIN_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    Queue strategyGenerationQueue() {
        return QueueBuilder
                .durable(StrategyGenerationMessagingProperties.MAIN_QUEUE)
                .deadLetterExchange(
                        StrategyGenerationMessagingProperties.DEAD_LETTER_EXCHANGE
                )
                .deadLetterRoutingKey(
                        StrategyGenerationMessagingProperties.DEAD_LETTER_ROUTING_KEY
                )
                .build();
    }

    @Bean
    Binding strategyGenerationBinding(
            Queue strategyGenerationQueue,
            DirectExchange strategyGenerationExchange
    ) {
        return BindingBuilder.bind(strategyGenerationQueue)
                .to(strategyGenerationExchange)
                .with(StrategyGenerationMessagingProperties.MAIN_ROUTING_KEY);
    }

    @Bean
    DirectExchange strategyGenerationRetryExchange() {
        return new DirectExchange(
                StrategyGenerationMessagingProperties.RETRY_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    Queue strategyGenerationRetryQueue(
            StrategyGenerationMessagingProperties properties
    ) {
        // 별도 스케줄러 없이 Queue TTL과 Dead Letter 라우팅으로 지연 재시도
        return QueueBuilder
                .durable(StrategyGenerationMessagingProperties.RETRY_QUEUE)
                .ttl(Math.toIntExact(properties.getRetryDelay().toMillis()))
                .deadLetterExchange(StrategyGenerationMessagingProperties.MAIN_EXCHANGE)
                .deadLetterRoutingKey(StrategyGenerationMessagingProperties.MAIN_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding strategyGenerationRetryBinding(
            Queue strategyGenerationRetryQueue,
            DirectExchange strategyGenerationRetryExchange
    ) {
        return BindingBuilder.bind(strategyGenerationRetryQueue)
                .to(strategyGenerationRetryExchange)
                .with(StrategyGenerationMessagingProperties.RETRY_ROUTING_KEY);
    }

    @Bean
    DirectExchange strategyGenerationDeadLetterExchange() {
        return new DirectExchange(
                StrategyGenerationMessagingProperties.DEAD_LETTER_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    Queue strategyGenerationDeadLetterQueue() {
        return QueueBuilder
                .durable(StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Binding strategyGenerationDeadLetterBinding(
            Queue strategyGenerationDeadLetterQueue,
            DirectExchange strategyGenerationDeadLetterExchange
    ) {
        return BindingBuilder.bind(strategyGenerationDeadLetterQueue)
                .to(strategyGenerationDeadLetterExchange)
                .with(StrategyGenerationMessagingProperties.DEAD_LETTER_ROUTING_KEY);
    }
}

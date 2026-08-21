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
 * AI 전략 생성 작업의 Main, Retry, Dead Letter topology
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

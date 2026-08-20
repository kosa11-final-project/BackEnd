package com.stockit.backend.feature.strategy.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class StrategyGenerationRabbitConfigurationTest {

    private final StrategyGenerationRabbitConfiguration configuration =
            new StrategyGenerationRabbitConfiguration();

    @Test
    void mainQueueDeadLettersFinalFailures() {
        Queue queue = configuration.strategyGenerationQueue();

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry(
                        "x-dead-letter-exchange",
                        StrategyGenerationMessagingProperties.DEAD_LETTER_EXCHANGE
                )
                .containsEntry(
                        "x-dead-letter-routing-key",
                        StrategyGenerationMessagingProperties.DEAD_LETTER_ROUTING_KEY
                );
    }

    @Test
    void retryQueueWaitsThenReturnsMessageToMainExchange() {
        StrategyGenerationMessagingProperties properties =
                new StrategyGenerationMessagingProperties();
        properties.setRetryDelay(Duration.ofSeconds(30));

        Queue queue = configuration.strategyGenerationRetryQueue(properties);

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-message-ttl", 30_000)
                .containsEntry(
                        "x-dead-letter-exchange",
                        StrategyGenerationMessagingProperties.MAIN_EXCHANGE
                )
                .containsEntry(
                        "x-dead-letter-routing-key",
                        StrategyGenerationMessagingProperties.MAIN_ROUTING_KEY
                );
    }
}

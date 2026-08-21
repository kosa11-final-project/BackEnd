package com.stockit.backend.feature.strategy.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 전략 생성 RabbitMQ topology와 재시도 정책
 */
@ConfigurationProperties(prefix = "app.ai-strategy.messaging")
public class StrategyGenerationMessagingProperties {

    public static final String MAIN_EXCHANGE = "stockit.ai-strategy.exchange";
    public static final String MAIN_ROUTING_KEY = "strategy.generate.v1";
    public static final String MAIN_QUEUE = "stockit.ai-strategy.generate.v1";
    public static final String RETRY_EXCHANGE = "stockit.ai-strategy.retry.exchange";
    public static final String RETRY_ROUTING_KEY = "strategy.generate.v1.retry";
    public static final String RETRY_QUEUE = "stockit.ai-strategy.generate.v1.retry";
    public static final String DEAD_LETTER_EXCHANGE = "stockit.ai-strategy.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "strategy.generate.v1.dlq";
    public static final String DEAD_LETTER_QUEUE = "stockit.ai-strategy.generate.dlq";
    public static final String RETRY_COUNT_HEADER = "x-strategy-retry-count";

    private boolean enabled = true;
    private int maxAttempts = 3;
    private Duration retryDelay = Duration.ofSeconds(30);
    private Duration confirmTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        requirePositive(retryDelay, "retryDelay");
        this.retryDelay = retryDelay;
    }

    public Duration getConfirmTimeout() {
        return confirmTimeout;
    }

    public void setConfirmTimeout(Duration confirmTimeout) {
        requirePositive(confirmTimeout, "confirmTimeout");
        this.confirmTimeout = confirmTimeout;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

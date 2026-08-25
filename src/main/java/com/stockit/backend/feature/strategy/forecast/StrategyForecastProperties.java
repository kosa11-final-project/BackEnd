package com.stockit.backend.feature.strategy.forecast;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * AI 전략 생성용 일별 수요예측 API와 Redis 보존 설정
 */
@ConfigurationProperties(prefix = "app.ai-strategy.forecast")
public class StrategyForecastProperties {

    static final Duration LOCK_SAFETY_MARGIN = Duration.ofSeconds(30);

    private String baseUrl = "";
    private String path = "/api/v1/demand-forecasts/daily";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(60);
    private Duration resultTtl = Duration.ofDays(3);
    private Duration lockTtl = Duration.ofSeconds(180);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = requirePositive(readTimeout, "readTimeout");
    }

    public Duration getResultTtl() {
        return resultTtl;
    }

    public void setResultTtl(Duration resultTtl) {
        this.resultTtl = requirePositive(resultTtl, "resultTtl");
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = requirePositive(lockTtl, "lockTtl");
    }

    @PostConstruct
    void validateLockTtl() {
        Duration minimumLockTtl = connectTimeout
                .plus(readTimeout)
                .plus(LOCK_SAFETY_MARGIN);
        if (lockTtl.compareTo(minimumLockTtl) < 0) {
            throw new IllegalStateException(
                    "Forecast lockTtl is too short: required at least "
                            + minimumLockTtl.toSeconds()
                            + "s, actual "
                            + lockTtl.toSeconds()
                            + "s"
            );
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

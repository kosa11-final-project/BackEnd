package com.stockit.backend.feature.strategy.result;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-strategy.result")
public class StrategyResultProperties {

    private Duration ttl = Duration.ofDays(3);
    private Duration lockTtl = Duration.ofMinutes(5);

    public Duration getTtl() { return ttl; }
    public void setTtl(Duration value) { ttl = positive(value, "ttl"); }
    public Duration getLockTtl() { return lockTtl; }
    public void setLockTtl(Duration value) { lockTtl = positive(value, "lockTtl"); }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

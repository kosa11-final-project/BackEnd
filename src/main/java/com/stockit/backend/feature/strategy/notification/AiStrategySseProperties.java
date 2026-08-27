package com.stockit.backend.feature.strategy.notification;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 전략 SSE 연결 수명과 브라우저 재연결 정책 */
@ConfigurationProperties(prefix = "app.ai-strategy.sse")
public class AiStrategySseProperties {

    private Duration timeout = Duration.ofMinutes(30);
    private long reconnectTimeMillis = 3000L;
    private int maxConnectionsPerSession = 5;

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
    }

    public long getReconnectTimeMillis() {
        return reconnectTimeMillis;
    }

    public void setReconnectTimeMillis(long reconnectTimeMillis) {
        if (reconnectTimeMillis < 1L) {
            throw new IllegalArgumentException(
                    "reconnectTimeMillis must be positive"
            );
        }
        this.reconnectTimeMillis = reconnectTimeMillis;
    }

    public int getMaxConnectionsPerSession() {
        return maxConnectionsPerSession;
    }

    public void setMaxConnectionsPerSession(int maxConnectionsPerSession) {
        if (maxConnectionsPerSession < 1 || maxConnectionsPerSession > 20) {
            throw new IllegalArgumentException(
                    "maxConnectionsPerSession must be between 1 and 20"
            );
        }
        this.maxConnectionsPerSession = maxConnectionsPerSession;
    }
}

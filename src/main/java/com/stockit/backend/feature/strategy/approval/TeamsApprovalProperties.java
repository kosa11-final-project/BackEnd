package com.stockit.backend.feature.strategy.approval;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Power Automate Teams 개인 채팅 웹후크 연결 설정. */
@ConfigurationProperties(prefix = "app.ai-strategy.teams")
public class TeamsApprovalProperties {

    private boolean enabled;
    private String webhookUrl = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(15);
    private int maxReviewers = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = positive(connectTimeout, "connectTimeout");
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = positive(readTimeout, "readTimeout");
    }

    public int getMaxReviewers() {
        return maxReviewers;
    }

    public void setMaxReviewers(int maxReviewers) {
        if (maxReviewers < 1 || maxReviewers > 10) {
            throw new IllegalArgumentException("maxReviewers must be between 1 and 10");
        }
        this.maxReviewers = maxReviewers;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

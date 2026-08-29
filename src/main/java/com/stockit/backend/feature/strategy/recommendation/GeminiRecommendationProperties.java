package com.stockit.backend.feature.strategy.recommendation;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gemini Interactions API 연결 설정. API 키는 환경 또는 local profile에서만 주입한다. */
@ConfigurationProperties(prefix = "ai.recommendation")
public class GeminiRecommendationProperties {

    private static final Set<String> THINKING_LEVELS = Set.of(
            "minimal", "low", "medium", "high"
    );

    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String path = "/v1beta/interactions";
    private String apiKey = "";
    private String model = "gemini-3.5-flash";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(120);
    private int maxOutputTokens = 4096;
    private int seed = 72;
    private String thinkingLevel = "minimal";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = positive(value, "connectTimeout"); }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { readTimeout = positive(value, "readTimeout"); }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) {
        if (value <= 0) throw new IllegalArgumentException("maxOutputTokens must be positive");
        maxOutputTokens = value;
    }
    public int getSeed() { return seed; }
    public void setSeed(int seed) { this.seed = seed; }
    public String getThinkingLevel() { return thinkingLevel; }
    public void setThinkingLevel(String value) {
        if (value == null || !THINKING_LEVELS.contains(
                value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "thinkingLevel must be one of minimal, low, medium, high"
            );
        }
        thinkingLevel = value.toLowerCase(Locale.ROOT);
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

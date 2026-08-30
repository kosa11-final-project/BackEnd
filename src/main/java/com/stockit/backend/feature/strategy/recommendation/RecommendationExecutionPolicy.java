package com.stockit.backend.feature.strategy.recommendation;

/** 현재 메시지 시도가 Gemini 일시 장애를 서버 fallback으로 끝내야 하는지 지정한다. */
public record RecommendationExecutionPolicy(boolean fallbackOnTransientLlmFailure) {

    public static RecommendationExecutionPolicy retryTransientLlmFailure() {
        return new RecommendationExecutionPolicy(false);
    }

    public static RecommendationExecutionPolicy fallbackTransientLlmFailure() {
        return new RecommendationExecutionPolicy(true);
    }
}

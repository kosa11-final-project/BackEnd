package com.stockit.backend.feature.strategy.recommendation;

/** 구조화 JSON은 파싱됐지만 서버의 추천 의미 규칙을 위반한 경우. */
public class InvalidAiRecommendationException extends RuntimeException {

    public InvalidAiRecommendationException(String message) {
        super(message);
    }

    public InvalidAiRecommendationException(String message, Throwable cause) {
        super(message, cause);
    }
}

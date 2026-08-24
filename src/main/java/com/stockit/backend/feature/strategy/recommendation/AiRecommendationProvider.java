package com.stockit.backend.feature.strategy.recommendation;

/** 외부 LLM 공급자 경계. */
public interface AiRecommendationProvider {

    AiRecommendationProviderResponse recommend(AiRecommendationRequest request);
}

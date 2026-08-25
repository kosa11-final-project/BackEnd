package com.stockit.backend.feature.strategy.approval;

import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** 최종 대안이 정상 LLM 선택인지 결정론적 서버 fallback인지 구분한다. */
public enum StrategyRecommendationSource {
    LLM,
    SERVER_FALLBACK;

    public static StrategyRecommendationSource from(StrategyGenerationResult result) {
        if (result != null && result.providerMetadata() != null
                && "server-rule-fallback".equalsIgnoreCase(
                result.providerMetadata().model())) {
            return SERVER_FALLBACK;
        }
        return LLM;
    }
}

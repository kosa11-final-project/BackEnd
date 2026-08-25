package com.stockit.backend.feature.strategy.recommendation;

import java.util.List;

/** LLM이 선택하는 것은 후보 ID와 설명뿐이며 정량 값은 서버 계산 결과를 사용한다. */
public record AiRecommendationProviderResponse(
        String interactionId,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        List<Recommendation> recommendations
) {
    public AiRecommendationProviderResponse {
        recommendations = recommendations == null ? null : List.copyOf(recommendations);
    }

    public record Recommendation(
            String candidateId,
            int rank,
            String optionName,
            String recommendationReason,
            String advantage,
            String caution
    ) {
    }
}

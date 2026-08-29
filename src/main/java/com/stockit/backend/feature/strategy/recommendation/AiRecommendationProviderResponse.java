package com.stockit.backend.feature.strategy.recommendation;

import java.util.List;

/** LLM이 선택하는 것은 후보 ID와 설명뿐이며 정량 값은 서버 계산 결과를 사용한다. */
public record AiRecommendationProviderResponse(
        String interactionId,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer thoughtTokens,
        Integer totalTokens,
        List<Recommendation> recommendations
) {
    public AiRecommendationProviderResponse {
        recommendations = recommendations == null ? null : List.copyOf(recommendations);
    }

    /** 사고 Token을 제공하지 않던 기존 Provider·테스트와의 소스 호환용 생성자. */
    public AiRecommendationProviderResponse(
            String interactionId,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            List<Recommendation> recommendations
    ) {
        this(
                interactionId, model, inputTokens, outputTokens,
                null, sum(inputTokens, outputTokens), recommendations
        );
    }

    private static Integer sum(Integer left, Integer right) {
        return left == null || right == null ? null : left + right;
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

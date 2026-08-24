package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDateTime;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

public record AiStrategyCaseResponse(
        Long strategyCaseId,
        Long skuId,
        String caseName,
        StrategyCaseStatus caseStatus,
        StrategyGenerationStage generationStage,
        String failureCode,
        String failureMessage,
        LocalDateTime resultExpiresAt,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        StrategyGenerationResult result
) {
    public static AiStrategyCaseResponse from(
            StrategyCaseVO strategyCase,
            StrategyGenerationResult result
    ) {
        return new AiStrategyCaseResponse(
                strategyCase.getStrategyCaseId(), strategyCase.getSkuId(),
                strategyCase.getCaseName(), strategyCase.getCaseStatus(),
                strategyCase.getGenerationStage(), strategyCase.getFailureCode(),
                strategyCase.getFailureMessage(), strategyCase.getResultExpiresAt(),
                strategyCase.getCreatedAt(), strategyCase.getCompletedAt(), result
        );
    }
}

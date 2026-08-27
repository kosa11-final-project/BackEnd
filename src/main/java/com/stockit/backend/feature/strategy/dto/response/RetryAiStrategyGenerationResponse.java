package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/** 사용자 재시도로 생성되었거나 이미 존재하는 신규 Case 응답. */
public record RetryAiStrategyGenerationResponse(
        Long originalStrategyCaseId,
        Long strategyCaseId,
        Long retryParentStrategyCaseId,
        String caseName,
        StrategyCaseStatus caseStatus,
        StrategyGenerationStage generationStage,
        LocalDateTime createdAt,
        boolean reusedExistingRetry,
        DateAdjustment dateAdjustment
) {
    public static RetryAiStrategyGenerationResponse created(
            Long parentCaseId,
            StrategyCaseCreated created,
            DateAdjustment dateAdjustment
    ) {
        return new RetryAiStrategyGenerationResponse(
                parentCaseId,
                created.strategyCaseId(),
                parentCaseId,
                created.caseName(),
                created.caseStatus(),
                created.generationStage(),
                created.createdAt(),
                false,
                dateAdjustment
        );
    }

    public static RetryAiStrategyGenerationResponse existing(
            Long parentCaseId,
            StrategyCaseVO existing,
            DateAdjustment dateAdjustment
    ) {
        return new RetryAiStrategyGenerationResponse(
                parentCaseId,
                existing.getStrategyCaseId(),
                parentCaseId,
                existing.getCaseName(),
                existing.getCaseStatus(),
                existing.getGenerationStage(),
                existing.getCreatedAt(),
                true,
                dateAdjustment
        );
    }

    public record DateAdjustment(
            boolean applied,
            LocalDate originalPreferredStartDate,
            LocalDate originalPreferredEndDate,
            LocalDate adjustedPreferredStartDate,
            LocalDate adjustedPreferredEndDate
    ) {
    }
}

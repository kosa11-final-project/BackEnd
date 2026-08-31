package com.stockit.backend.feature.strategy.alert;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** AI 전략 생성 최종 실패를 Teams Adaptive Card로 전달하기 위한 불변 입력 */
public record AiStrategyTeamsAlertMessage(
        String eventType,
        String severity,
        String environment,
        LocalDateTime occurredAt,
        Long strategyCaseId,
        Long retryParentCaseId,
        String caseCode,
        String caseName,
        Long requesterId,
        String requesterName,
        Long skuId,
        String skuCode,
        String skuName,
        Long sourceSalesPointId,
        String sourceSalesPointName,
        AiStrategyFailureCategory failureCategory,
        String failedStage,
        String failureCode,
        String rootFailureCode,
        String failureMessage,
        Integer requestedLotCount,
        Integer requestedCandidateSalesPointCount,
        Integer requestedStrategyTypeCount,
        String salesPointSelectionMode,
        String strategyTypeSelectionMode,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate,
        String deduplicationKey,
        String caseUrl,
        String logUrl
) {
}

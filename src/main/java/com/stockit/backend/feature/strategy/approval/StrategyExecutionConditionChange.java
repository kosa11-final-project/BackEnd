package com.stockit.backend.feature.strategy.approval;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 사용자가 이전 조건과 현재 조건을 비교할 수 있는 단일 변경 항목. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StrategyExecutionConditionChange(
        StrategyExecutionConditionChangeType type,
        String field,
        String label,
        StrategyExecutionConditionSubject subject,
        Object previousValue,
        Object currentValue,
        Object requestedValue,
        Object suggestedValue,
        String unit,
        String reason
) {
    public StrategyExecutionConditionChange {
        if (type == null || field == null || field.isBlank()
                || label == null || label.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "execution condition change metadata is invalid"
            );
        }
    }
}

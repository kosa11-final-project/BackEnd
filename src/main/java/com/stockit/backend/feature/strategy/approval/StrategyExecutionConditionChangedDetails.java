package com.stockit.backend.feature.strategy.approval;

import java.time.LocalDateTime;
import java.util.List;

/** 최종 선택이 거부된 시점과 모든 실행 조건 변경 내역. */
public record StrategyExecutionConditionChangedDetails(
        Long strategyCaseId,
        String optionId,
        LocalDateTime validatedAt,
        List<StrategyExecutionConditionChange> changes
) {
    public StrategyExecutionConditionChangedDetails {
        if (strategyCaseId == null || strategyCaseId <= 0
                || optionId == null || optionId.isBlank()
                || validatedAt == null || changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException(
                    "execution condition changed details are invalid"
            );
        }
        changes = List.copyOf(changes);
    }
}

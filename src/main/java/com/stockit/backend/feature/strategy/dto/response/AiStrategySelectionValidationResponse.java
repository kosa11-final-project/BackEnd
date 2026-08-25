package com.stockit.backend.feature.strategy.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.stockit.backend.feature.strategy.approval.StrategySelectionInputSource;

/** DB 쓰기 없이 최신 실행 가능성 검증을 통과한 최종안 요약. */
public record AiStrategySelectionValidationResponse(
        Long strategyCaseId,
        String optionId,
        boolean valid,
        StrategySelectionInputSource selectionSource,
        BigDecimal actionQuantity,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime validatedAt
) {
}

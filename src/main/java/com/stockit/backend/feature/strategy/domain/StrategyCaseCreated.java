package com.stockit.backend.feature.strategy.domain;

import java.time.LocalDateTime;

/**
 * 전략 생성 요청 저장 직후 후속 처리에 전달하는 최소 결과
 */
public record StrategyCaseCreated(
        Long strategyCaseId,
        String caseName,
        StrategyCaseStatus caseStatus,
        LocalDateTime createdAt
) {
}

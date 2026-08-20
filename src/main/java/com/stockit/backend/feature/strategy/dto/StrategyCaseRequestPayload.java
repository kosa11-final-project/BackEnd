package com.stockit.backend.feature.strategy.dto;

import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 정규 컬럼으로 분리되지 않은 사용자 선택값을 보존하기 위한 요청 스냅샷
 */
public record StrategyCaseRequestPayload(
        List<Long> lotIds,
        List<Long> candidateSalesPointIds,
        List<StrategyType> strategyTypes,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate
) {
}

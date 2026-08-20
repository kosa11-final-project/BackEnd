package com.stockit.backend.feature.strategy.dto;

import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 사용자 선택값과 요청 시점에 확정한 수요예측 기간을 보존하는 요청 스냅샷
 */
public record StrategyCaseRequestPayload(
        List<Long> lotIds,
        List<Long> candidateSalesPointIds,
        List<StrategyType> strategyTypes,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate,
        LocalDate forecastStartDate,
        LocalDate forecastEndDate
) {

    public StrategyCaseRequestPayload {
        // 검증 이후 호출자 측 변경으로 저장될 스냅샷이 달라지는 것을 방지
        lotIds = List.copyOf(lotIds);
        candidateSalesPointIds = List.copyOf(candidateSalesPointIds);
        strategyTypes = List.copyOf(strategyTypes);
    }
}

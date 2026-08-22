package com.stockit.backend.feature.statistics.dto.response;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "종료된 AI 전략 성과 통계")
public record StrategyStatisticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String scopeType,
        String scopeCode,
        StrategyStatisticsSummaryResponse summary,
        List<StrategyStatisticsTrendPointResponse> dailyTrend,
        List<StrategyActionCombinationStatisticsResponse> actionCombinationBreakdown
) {
}

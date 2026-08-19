package com.stockit.backend.feature.statistics.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "재고 통계 화면 전체 응답")
public record InventoryStatisticsResponse(
        LocalDate asOfDate,
        Instant calculatedAt,
        boolean canViewFinancials,
        @Schema(description = "추이를 조회한 통계 범위 유형", example = "NATIONAL")
        String trendScopeType,
        @Schema(description = "추이를 조회한 통계 범위 코드", example = "ALL")
        String trendScopeCode,
        @Schema(description = "NATIONAL, WAREHOUSE, OFFLINE_STORE, ONLINE_STORE, UNASSIGNED별 전체 요약")
        Map<String, InventoryStatisticsSummaryResponse> scopeSummaries,
        List<InventoryStatisticsLocationResponse> locations,
        List<InventoryStatisticsTrendPointResponse> dailyTrend
) {
}

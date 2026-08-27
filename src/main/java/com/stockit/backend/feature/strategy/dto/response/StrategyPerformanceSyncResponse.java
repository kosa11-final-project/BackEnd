package com.stockit.backend.feature.strategy.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전략 성과 수동 동기화 결과")
public record StrategyPerformanceSyncResponse(
        Instant syncedAt,
        int processedStrategyCount,
        int updatedPerformanceCount,
        int skippedStrategyCount,
        List<String> warnings
) {
}

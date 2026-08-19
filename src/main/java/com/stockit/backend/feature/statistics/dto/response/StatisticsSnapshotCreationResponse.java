package com.stockit.backend.feature.statistics.dto.response;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "통계 스냅샷 생성 결과")
public record StatisticsSnapshotCreationResponse(
        Long syncJobId,
        LocalDate asOfDate,
        int snapshotCount,
        List<Long> snapshotIds
) {
}

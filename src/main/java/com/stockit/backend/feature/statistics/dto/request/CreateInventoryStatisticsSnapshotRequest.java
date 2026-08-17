package com.stockit.backend.feature.statistics.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateInventoryStatisticsSnapshotRequest(
        @NotNull(message = "동기화 작업 ID는 필수입니다.")
        @Positive(message = "동기화 작업 ID는 양수여야 합니다.")
        Long syncJobId,
        @NotNull(message = "통계 집계 기준일은 필수입니다.")
        LocalDate asOfDate
) {
}

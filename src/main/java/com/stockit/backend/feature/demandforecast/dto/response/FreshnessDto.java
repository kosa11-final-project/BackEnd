package com.stockit.backend.feature.demandforecast.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "데이터 최신성 및 품질 메타데이터")
public record FreshnessDto(
        @Schema(description = "데이터 최종 갱신 시각")
        Instant lastUpdatedAt,

        @Schema(description = "데이터가 오래되었는지 여부 (Stale)", example = "false")
        boolean isStale,

        @Schema(description = "데이터 품질 상태 (AVAILABLE, NO_DATA, STALE, ERROR)", example = "AVAILABLE")
        String dataQualityState,

        @Schema(description = "설명 메시지", example = "정상적으로 최신 수요예측과 안전재고 기준이 조회되었습니다.")
        String message,

        @Schema(description = "예측 기준일")
        LocalDate forecastAsOf
) {
    public FreshnessDto(Instant lastUpdatedAt, boolean isStale, String dataQualityState, String message) {
        this(lastUpdatedAt, isStale, dataQualityState, message, null);
    }
}

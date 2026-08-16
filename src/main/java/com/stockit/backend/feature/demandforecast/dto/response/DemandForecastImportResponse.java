package com.stockit.backend.feature.demandforecast.dto.response;

import java.time.LocalDate;

import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;

import io.swagger.v3.oas.annotations.media.Schema;

public record DemandForecastImportResponse(
        @Schema(description = "처리한 Azure ML Job ID", example = "purple_monkey_gyk4m5yyxr")
        String azureJobId,
        @Schema(description = "ML 모델명", example = "stockit-demand-lightgbm")
        String modelName,
        @Schema(description = "ML 모델 버전", example = "1")
        String modelVersion,
        @Schema(description = "DB에서 조회한 모델 버전 ID", example = "7")
        Long modelVersionId,
        @Schema(description = "예측 기준일", example = "2026-07-31", type = "string", format = "date")
        LocalDate forecastBaseDate,
        @Schema(description = "처리한 배치 번호", example = "1")
        Integer batchNumber,
        @Schema(description = "전체 배치 수", example = "10")
        Integer totalBatches,
        @Schema(description = "해당 요청에서 처리한 예측 건수", example = "1000")
        int importedCount
) {

    public static DemandForecastImportResponse from(
            DemandForecastImportRequest request,
            Long modelVersionId,
            int importedCount
    ) {
        return new DemandForecastImportResponse(
                request.azureJobId(),
                request.modelName(),
                request.modelVersion(),
                modelVersionId,
                request.forecastBaseDate(),
                request.batchNumber(),
                request.totalBatches(),
                importedCount
        );
    }
}

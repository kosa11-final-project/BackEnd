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
        @Schema(description = "전체 예측 데이터 건수", example = "9842")
        Long totalItems,
        @Schema(description = "해당 요청에서 수신한 예측 건수", example = "1000")
        int importedCount,
        @Schema(description = "현재까지 수신한 고유 배치 수", example = "1")
        int receivedBatches,
        @Schema(description = "현재까지 수신한 예측 건수", example = "1000")
        long receivedItems,
        @Schema(description = "동일 내용의 재전송 여부", example = "false")
        boolean duplicate,
        @Schema(description = "전체 실행 상태", example = "RUNNING")
        String runStatus
) {

    public static DemandForecastImportResponse from(
            DemandForecastImportRequest request,
            Long modelVersionId,
            int importedCount,
            int receivedBatches,
            long receivedItems,
            boolean duplicate,
            String runStatus
    ) {
        return new DemandForecastImportResponse(
                request.azureJobId(),
                request.modelName(),
                request.modelVersion(),
                modelVersionId,
                request.forecastBaseDate(),
                request.batchNumber(),
                request.totalBatches(),
                request.totalItems(),
                importedCount,
                receivedBatches,
                receivedItems,
                duplicate,
                runStatus
        );
    }
}

package com.stockit.backend.feature.demandforecast.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DemandForecastImportRequest(
        @Schema(description = "Azure ML 추론 Job ID", example = "purple_monkey_gyk4m5yyxr", maxLength = 255)
        @NotBlank(message = "Azure Job ID는 필수입니다.")
        @Size(max = 255, message = "Azure Job ID는 255자 이하여야 합니다.")
        String azureJobId,

        @Schema(description = "ML 모델명", example = "stockit-demand-lightgbm", maxLength = 100)
        @NotBlank(message = "모델명은 필수입니다.")
        @Size(max = 100, message = "모델명은 100자 이하여야 합니다.")
        String modelName,

        @Schema(description = "ML 모델 버전", example = "1", maxLength = 50)
        @NotBlank(message = "모델 버전은 필수입니다.")
        @Size(max = 50, message = "모델 버전은 50자 이하여야 합니다.")
        String modelVersion,

        @Schema(description = "예측 기준일", example = "2026-07-31", type = "string", format = "date")
        @NotNull(message = "예측 기준일은 필수입니다.")
        LocalDate forecastBaseDate,

        @Schema(description = "현재 배치 번호", example = "1", minimum = "1")
        @NotNull(message = "배치 번호는 필수입니다.")
        @Min(value = 1, message = "배치 번호는 1 이상이어야 합니다.")
        Integer batchNumber,

        @Schema(description = "전체 배치 수", example = "10", minimum = "1")
        @NotNull(message = "전체 배치 수는 필수입니다.")
        @Min(value = 1, message = "전체 배치 수는 1 이상이어야 합니다.")
        Integer totalBatches,

        @Schema(description = "전체 예측 데이터 건수(생략 시 마지막 배치에서 실제 수신 건수로 확정)",
                example = "9842", minimum = "1")
        @Min(value = 1, message = "전체 예측 데이터 건수는 1 이상이어야 합니다.")
        Long totalItems,

        @ArraySchema(
                schema = @Schema(implementation = DemandForecastImportItemRequest.class),
                minItems = 1,
                maxItems = 1000
        )
        @NotEmpty(message = "예측 목록은 비어 있을 수 없습니다.")
        @Size(max = 1000, message = "배치당 예측 데이터는 최대 1,000건입니다.")
        List<@Valid @NotNull(message = "예측 항목은 NULL일 수 없습니다.") DemandForecastImportItemRequest> forecasts
) {

    @JsonIgnore
    @AssertTrue(message = "배치 번호는 전체 배치 수보다 클 수 없습니다.")
    public boolean isValidBatchRange() {
        return batchNumber == null || totalBatches == null || batchNumber <= totalBatches;
    }

    @JsonIgnore
    @AssertTrue(message = "전체 예측 건수는 배치 수와 배치당 최대 1,000건 범위에 맞아야 합니다.")
    public boolean isValidTotalItemRange() {
        if (totalBatches == null || totalItems == null || forecasts == null) {
            return true;
        }

        long remainingBatches = totalBatches - 1L;
        long minimumTotalItems = forecasts.size() + remainingBatches;
        long maximumTotalItems = forecasts.size() + remainingBatches * 1000L;
        return totalItems >= minimumTotalItems && totalItems <= maximumTotalItems;
    }
}

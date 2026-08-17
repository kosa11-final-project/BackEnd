package com.stockit.backend.feature.demandforecast.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "구간별 누적 수요예측 수량")
public record CumulativeForecastDto(
        @Schema(description = "D+7 누적 예측 수량", example = "50")
        BigDecimal predictedQtyD7,

        @Schema(description = "D+14 누적 예측 수량", example = "105")
        BigDecimal predictedQtyD14,

        @Schema(description = "D+30 누적 예측 수량", example = "230")
        BigDecimal predictedQtyD30,

        @Schema(description = "D+60 누적 예측 수량", example = "480")
        BigDecimal predictedQtyD60,

        @Schema(description = "D+90 누적 예측 수량", example = "750")
        BigDecimal predictedQtyD90
) {
}

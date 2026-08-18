package com.stockit.backend.feature.demandforecast.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 예측 시점별 예상 가용 재고 잔고 DTO입니다.
 *
 * @param projectedD7 D+7 시점 예상 가용재고
 * @param projectedD14 D+14 시점 예상 가용재고
 * @param projectedD30 D+30 시점 예상 가용재고
 * @param projectedD60 D+60 시점 예상 가용재고
 * @param projectedD90 D+90 시점 예상 가용재고
 * @param stockoutPeriod 예상 재고 소진 구간 (예: D+14~D+30)
 */
@Schema(description = "예측 시점별 예상 가용재고")
public record ProjectedInventoryDto(
        @Schema(description = "D+7 시점 예상 가용재고", example = "60")
        BigDecimal projectedD7,

        @Schema(description = "D+14 시점 예상 가용재고", example = "5")
        BigDecimal projectedD14,

        @Schema(description = "D+30 시점 예상 가용재고", example = "0")
        BigDecimal projectedD30,

        @Schema(description = "D+60 시점 예상 가용재고", example = "0")
        BigDecimal projectedD60,

        @Schema(description = "D+90 시점 예상 가용재고", example = "0")
        BigDecimal projectedD90,

        @Schema(description = "예상 소진 구간 (예: D+14~D+30)", example = "D+14~D+30")
        String stockoutPeriod
) {
}

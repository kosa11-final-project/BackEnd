package com.stockit.backend.feature.demandforecast.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record DemandForecastImportItemRequest(
        @Schema(description = "SKU ID", example = "101", minimum = "1")
        @NotNull(message = "SKU ID는 필수입니다.")
        @Positive(message = "SKU ID는 양수여야 합니다.")
        Long skuId,

        @Schema(description = "판매처 ID", example = "10", minimum = "1")
        @NotNull(message = "판매처 ID는 필수입니다.")
        @Positive(message = "판매처 ID는 양수여야 합니다.")
        Long salesPointId,

        @Schema(description = "기준일부터 D+7까지의 누적 예측 수량", example = "12.300", minimum = "0")
        @NotNull(message = "D7 예측값은 필수입니다.")
        @DecimalMin(value = "0", message = "D7 예측값은 0 이상이어야 합니다.")
        @Digits(integer = 12, fraction = 3, message = "D7 예측값은 정수 12자리, 소수 3자리 이하여야 합니다.")
        BigDecimal predictedQtyD7,

        @Schema(description = "기준일부터 D+14까지의 누적 예측 수량", example = "24.800", minimum = "0")
        @NotNull(message = "D14 예측값은 필수입니다.")
        @DecimalMin(value = "0", message = "D14 예측값은 0 이상이어야 합니다.")
        @Digits(integer = 12, fraction = 3, message = "D14 예측값은 정수 12자리, 소수 3자리 이하여야 합니다.")
        BigDecimal predictedQtyD14,

        @Schema(description = "기준일부터 D+30까지의 누적 예측 수량", example = "51.200", minimum = "0")
        @NotNull(message = "D30 예측값은 필수입니다.")
        @DecimalMin(value = "0", message = "D30 예측값은 0 이상이어야 합니다.")
        @Digits(integer = 12, fraction = 3, message = "D30 예측값은 정수 12자리, 소수 3자리 이하여야 합니다.")
        BigDecimal predictedQtyD30,

        @Schema(description = "기준일부터 D+60까지의 누적 예측 수량", example = "103.700", minimum = "0")
        @NotNull(message = "D60 예측값은 필수입니다.")
        @DecimalMin(value = "0", message = "D60 예측값은 0 이상이어야 합니다.")
        @Digits(integer = 12, fraction = 3, message = "D60 예측값은 정수 12자리, 소수 3자리 이하여야 합니다.")
        BigDecimal predictedQtyD60,

        @Schema(description = "기준일부터 D+90까지의 누적 예측 수량", example = "157.100", minimum = "0")
        @NotNull(message = "D90 예측값은 필수입니다.")
        @DecimalMin(value = "0", message = "D90 예측값은 0 이상이어야 합니다.")
        @Digits(integer = 12, fraction = 3, message = "D90 예측값은 정수 12자리, 소수 3자리 이하여야 합니다.")
        BigDecimal predictedQtyD90,

        @Schema(
                description = "예측 생성 방식",
                example = "LIGHTGBM",
                allowableValues = {
                        "LIGHTGBM",
                        "SAME_SKU_OTHER_POINT",
                        "CATEGORY_SALES_POINT_MEDIAN",
                        "CATEGORY_GLOBAL_MEDIAN",
                        "MANUAL_INITIAL",
                        "DUMMY_BASELINE"
                }
        )
        @NotBlank(message = "예측 출처는 필수입니다.")
        @Pattern(
                regexp = "LIGHTGBM|SAME_SKU_OTHER_POINT|CATEGORY_SALES_POINT_MEDIAN|CATEGORY_GLOBAL_MEDIAN|MANUAL_INITIAL|DUMMY_BASELINE",
                message = "지원하지 않는 예측 출처입니다."
        )
        String forecastSource,

        @Schema(
                description = "예측 신뢰 수준",
                example = "HIGH",
                allowableValues = {"HIGH", "MEDIUM", "LOW"}
        )
        @NotBlank(message = "신뢰 수준은 필수입니다.")
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "지원하지 않는 신뢰 수준입니다.")
        String confidenceLevel
) {

    @JsonIgnore
    @AssertTrue(message = "예측값은 D7 ≤ D14 ≤ D30 ≤ D60 ≤ D90 순서여야 합니다.")
    public boolean isCumulativeQuantityOrderValid() {
        if (predictedQtyD7 == null || predictedQtyD14 == null || predictedQtyD30 == null
                || predictedQtyD60 == null || predictedQtyD90 == null) {
            return true;
        }
        return predictedQtyD7.compareTo(predictedQtyD14) <= 0
                && predictedQtyD14.compareTo(predictedQtyD30) <= 0
                && predictedQtyD30.compareTo(predictedQtyD60) <= 0
                && predictedQtyD60.compareTo(predictedQtyD90) <= 0;
    }
}

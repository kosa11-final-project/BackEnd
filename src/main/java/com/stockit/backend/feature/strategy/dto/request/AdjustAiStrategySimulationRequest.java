package com.stockit.backend.feature.strategy.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record AdjustAiStrategySimulationRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 3)
        @Schema(description = "전략 적용 수량", example = "29")
        BigDecimal actionQuantity,

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 1, fraction = 4)
        @Schema(description = "할인율. 할인 액션이 없는 전략은 null", example = "0.1500")
        BigDecimal discountRate,

        @NotNull
        @Schema(description = "조정 전략 시작일", example = "2026-08-20")
        LocalDate startDate,

        @NotNull
        @Schema(description = "조정 전략 종료일 또는 이동 전략 관측 종료일", example = "2026-08-27")
        LocalDate endDate
) {
    public AdjustStrategySimulationCommand toCommand() {
        return new AdjustStrategySimulationCommand(
                actionQuantity,
                discountRate,
                startDate,
                endDate
        );
    }
}

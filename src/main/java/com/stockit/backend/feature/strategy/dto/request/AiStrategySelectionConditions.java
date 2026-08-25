package com.stockit.backend.feature.strategy.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** 최종안 사전 검증과 Teams 확정 요청이 공유하는 사용자 조정 조건. */
public record AiStrategySelectionConditions(
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 3)
        BigDecimal actionQuantity,

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 1, fraction = 4)
        BigDecimal discountRate,

        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
    @JsonIgnore
    @AssertTrue(message = "전략 종료일은 시작일보다 빠를 수 없습니다.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null
                || !endDate.isBefore(startDate);
    }

    public AdjustStrategySimulationCommand toCommand() {
        return new AdjustStrategySimulationCommand(
                actionQuantity, discountRate, startDate, endDate
        );
    }
}

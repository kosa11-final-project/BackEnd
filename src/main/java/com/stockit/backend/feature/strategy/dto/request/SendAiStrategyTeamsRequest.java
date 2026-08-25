package com.stockit.backend.feature.strategy.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 최종 선택 후보와 Teams 개인 채팅 수신인 목록. */
public record SendAiStrategyTeamsRequest(
        @NotBlank @Size(max = 200) String optionId,
        @Valid AdjustedConditions adjustedConditions,
        @NotEmpty @Size(max = 10) List<@NotNull @Positive Long> reviewerIds
) {
    public SendAiStrategyTeamsRequest {
        reviewerIds = reviewerIds == null ? null : List.copyOf(reviewerIds);
    }

    public AdjustStrategySimulationCommand toAdjustmentCommand() {
        return adjustedConditions == null ? null : adjustedConditions.toCommand();
    }

    /** 조정 선택이면 화면에 표시된 최종 조건 전체를 전달한다. */
    public record AdjustedConditions(
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
        private AdjustStrategySimulationCommand toCommand() {
            return new AdjustStrategySimulationCommand(
                    actionQuantity, discountRate, startDate, endDate
            );
        }
    }
}

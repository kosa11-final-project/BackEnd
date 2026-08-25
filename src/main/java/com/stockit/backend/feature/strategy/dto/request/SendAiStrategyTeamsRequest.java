package com.stockit.backend.feature.strategy.dto.request;

import java.util.List;

import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 최종 선택 후보와 Teams 개인 채팅 수신인 목록. */
public record SendAiStrategyTeamsRequest(
        @NotBlank @Size(max = 200) String optionId,
        @Valid AiStrategySelectionConditions adjustedConditions,
        @NotEmpty @Size(max = 10) List<@NotNull @Positive Long> reviewerIds
) {
    public SendAiStrategyTeamsRequest {
        reviewerIds = reviewerIds == null ? null : List.copyOf(reviewerIds);
    }

    public AdjustStrategySimulationCommand toAdjustmentCommand() {
        return adjustedConditions == null ? null : adjustedConditions.toCommand();
    }
}

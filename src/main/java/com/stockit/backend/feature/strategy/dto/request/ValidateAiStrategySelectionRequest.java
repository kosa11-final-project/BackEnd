package com.stockit.backend.feature.strategy.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reviewer 선택 전에 실행하는 최종안 사전 검증 요청. */
public record ValidateAiStrategySelectionRequest(
        @NotBlank @Size(max = 200) String optionId,
        @Valid AiStrategySelectionConditions adjustedConditions
) {
    public com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand
            toAdjustmentCommand() {
        return adjustedConditions == null ? null : adjustedConditions.toCommand();
    }
}

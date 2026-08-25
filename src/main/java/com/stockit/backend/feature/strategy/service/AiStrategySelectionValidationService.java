package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.dto.response.AiStrategySelectionValidationResponse;
import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;

public interface AiStrategySelectionValidationService {

    AiStrategySelectionValidationResponse validate(
            Long strategyCaseId,
            String optionId,
            AdjustStrategySimulationCommand adjustedConditions,
            Long organizationId
    );
}

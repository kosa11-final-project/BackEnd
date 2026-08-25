package com.stockit.backend.feature.strategy.service;

import java.util.List;

import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;

public interface AiStrategyApprovalService {

    AiStrategyTeamsRequestResponse sendToTeams(
            Long strategyCaseId,
            String optionId,
            AdjustStrategySimulationCommand adjustedConditions,
            List<Long> reviewerIds,
            Long actorId,
            String actorName,
            Long organizationId
    );
}

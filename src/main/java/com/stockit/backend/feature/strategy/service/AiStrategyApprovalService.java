package com.stockit.backend.feature.strategy.service;

import java.util.List;

import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;

public interface AiStrategyApprovalService {

    AiStrategyTeamsRequestResponse sendToTeams(
            Long strategyCaseId,
            String optionId,
            List<Long> reviewerIds,
            Long actorId,
            String actorName,
            Long organizationId
    );
}

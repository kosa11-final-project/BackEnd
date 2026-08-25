package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;

public interface AiStrategyApprovalRetryService {

    AiStrategyTeamsRequestResponse retry(
            Long strategyCaseId,
            Long actorId,
            Long organizationId
    );
}

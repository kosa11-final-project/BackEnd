package com.stockit.backend.feature.strategy.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.PersistedStrategyApprovalReader;
import com.stockit.backend.feature.strategy.approval.PreparedTeamsDelivery;
import com.stockit.backend.feature.strategy.approval.ResolvedStrategySelection;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalPersistenceService;
import com.stockit.backend.feature.strategy.approval.StrategySelectionResolver;
import com.stockit.backend.feature.strategy.approval.StrategyTeamsDeliveryCoordinator;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalProperties;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.service.AiStrategyApprovalService;
import com.stockit.backend.feature.strategy.service.AiStrategyReviewerService;
import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

/** 최종 선택 DB 확정 후 Reviewer별 Teams 개인 채팅 전송을 조율한다. */
@Service
public class AiStrategyApprovalServiceImpl implements AiStrategyApprovalService {

    private final StrategySelectionResolver selectionResolver;
    private final AiStrategyReviewerService reviewerService;
    private final StrategyApprovalPersistenceService persistenceService;
    private final PersistedStrategyApprovalReader approvalReader;
    private final StrategyTeamsDeliveryCoordinator deliveryCoordinator;
    private final TeamsApprovalProperties properties;

    public AiStrategyApprovalServiceImpl(
            StrategySelectionResolver selectionResolver,
            AiStrategyReviewerService reviewerService,
            StrategyApprovalPersistenceService persistenceService,
            PersistedStrategyApprovalReader approvalReader,
            StrategyTeamsDeliveryCoordinator deliveryCoordinator,
            TeamsApprovalProperties properties
    ) {
        this.selectionResolver = selectionResolver;
        this.reviewerService = reviewerService;
        this.persistenceService = persistenceService;
        this.approvalReader = approvalReader;
        this.deliveryCoordinator = deliveryCoordinator;
        this.properties = properties;
    }

    @Override
    public AiStrategyTeamsRequestResponse sendToTeams(
            Long strategyCaseId,
            String optionId,
            AdjustStrategySimulationCommand adjustedConditions,
            List<Long> reviewerIds,
            Long actorId,
            Long organizationId
    ) {
        if (strategyCaseId == null || strategyCaseId <= 0
                || actorId == null || actorId <= 0
                || optionId == null || optionId.isBlank()) {
            throw new AppException(ErrorCode.AI_STRATEGY_INVALID_REQUEST);
        }
        ResolvedStrategySelection resolved = selectionResolver.resolve(
                strategyCaseId, optionId, adjustedConditions
        );
        List<AiStrategyReviewerVO> reviewers = reviewerService.requireReviewers(
                organizationId, reviewerIds, properties.getMaxReviewers()
        );

        persistenceService.prepare(
                strategyCaseId,
                actorId,
                organizationId,
                resolved,
                reviewers
        );
        PreparedTeamsDelivery delivery = approvalReader.read(
                strategyCaseId, organizationId
        );
        return deliveryCoordinator.deliver(delivery, actorId);
    }
}

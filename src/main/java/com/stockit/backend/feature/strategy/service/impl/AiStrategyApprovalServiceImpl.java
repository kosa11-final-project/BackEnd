package com.stockit.backend.feature.strategy.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.PreparedStrategyApproval;
import com.stockit.backend.feature.strategy.approval.ResolvedStrategySelection;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalDeliveryStateService;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalPersistenceService;
import com.stockit.backend.feature.strategy.approval.StrategySelectionResolver;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.approval.StrategyReviewStatus;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalMessage;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalProperties;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalSender;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalDeliveryException;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.DeliveryStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.ReviewerDelivery;
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
    private final StrategyApprovalDeliveryStateService deliveryStateService;
    private final TeamsApprovalSender teamsApprovalSender;
    private final TeamsApprovalProperties properties;

    public AiStrategyApprovalServiceImpl(
            StrategySelectionResolver selectionResolver,
            AiStrategyReviewerService reviewerService,
            StrategyApprovalPersistenceService persistenceService,
            StrategyApprovalDeliveryStateService deliveryStateService,
            TeamsApprovalSender teamsApprovalSender,
            TeamsApprovalProperties properties
    ) {
        this.selectionResolver = selectionResolver;
        this.reviewerService = reviewerService;
        this.persistenceService = persistenceService;
        this.deliveryStateService = deliveryStateService;
        this.teamsApprovalSender = teamsApprovalSender;
        this.properties = properties;
    }

    @Override
    public AiStrategyTeamsRequestResponse sendToTeams(
            Long strategyCaseId,
            String optionId,
            AdjustStrategySimulationCommand adjustedConditions,
            List<Long> reviewerIds,
            Long actorId,
            String actorName,
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

        PreparedStrategyApproval prepared = persistenceService.prepare(
                strategyCaseId,
                actorId,
                organizationId,
                resolved,
                reviewers
        );
        Map<Long, ReviewRequestRecord> requestsByReviewer = prepared
                .reviewRequests().stream()
                .collect(Collectors.toMap(
                        ReviewRequestRecord::getReviewerId,
                        Function.identity()
                ));

        List<ReviewerDelivery> deliveries = new ArrayList<>();
        for (AiStrategyReviewerVO reviewer : reviewers) {
            ReviewRequestRecord reviewRequest = requestsByReviewer.get(
                    reviewer.getReviewerId()
            );
            if (reviewRequest.getReviewStatus() == StrategyReviewStatus.SENT) {
                deliveries.add(delivery(reviewer, StrategyReviewStatus.SENT, null));
                continue;
            }
            boolean claimed = deliveryStateService.tryClaim(
                    reviewRequest.getReviewRequestId(),
                    actorId,
                    properties.getClaimTimeout()
            );
            if (!claimed) {
                deliveries.add(delivery(
                        reviewer, StrategyReviewStatus.SENDING, null
                ));
                continue;
            }
            try {
                teamsApprovalSender.send(new TeamsApprovalMessage(
                        reviewer.getEmail(),
                        strategyCaseId,
                        prepared.caseName(),
                        resolved.calculationContext().sku().skuCode(),
                        resolved.calculationContext().sku().skuName(),
                        actorName,
                        resolved
                ));
                deliveryStateService.markSent(
                        reviewRequest.getReviewRequestId(), actorId
                );
                deliveries.add(delivery(reviewer, StrategyReviewStatus.SENT, null));
            } catch (TeamsApprovalDeliveryException exception) {
                deliveryStateService.markFailed(
                        reviewRequest.getReviewRequestId(), actorId
                );
                deliveries.add(delivery(
                        reviewer,
                        StrategyReviewStatus.FAILED,
                        exception.getCode()
                ));
            }
        }

        boolean alreadyReady = prepared.caseStatus()
                == StrategyCaseStatus.READY_TO_EXECUTE;
        boolean becameReady = deliveryStateService.markReadyIfComplete(
                strategyCaseId, prepared.strategyOptionId(), actorId
        );
        boolean ready = alreadyReady || becameReady;
        return new AiStrategyTeamsRequestResponse(
                strategyCaseId,
                optionId,
                prepared.strategyOptionId(),
                prepared.finalSelectionId(),
                ready ? StrategyCaseStatus.READY_TO_EXECUTE
                        : StrategyCaseStatus.GENERATED,
                deliveryStatus(deliveries),
                deliveries
        );
    }

    private static ReviewerDelivery delivery(
            AiStrategyReviewerVO reviewer,
            StrategyReviewStatus status,
            String failureCode
    ) {
        return new ReviewerDelivery(
                reviewer.getReviewerId(),
                reviewer.getReviewerName(),
                reviewer.getEmail(),
                status,
                failureCode
        );
    }

    private static DeliveryStatus deliveryStatus(List<ReviewerDelivery> deliveries) {
        long sent = deliveries.stream()
                .filter(delivery -> delivery.deliveryStatus()
                        == StrategyReviewStatus.SENT)
                .count();
        if (sent == deliveries.size()) {
            return DeliveryStatus.SENT;
        }
        boolean sending = deliveries.stream()
                .anyMatch(delivery -> delivery.deliveryStatus()
                        == StrategyReviewStatus.SENDING);
        boolean failed = deliveries.stream()
                .anyMatch(delivery -> delivery.deliveryStatus()
                        == StrategyReviewStatus.FAILED);
        if (sending && !failed) {
            return DeliveryStatus.IN_PROGRESS;
        }
        return sent == 0 && !sending
                ? DeliveryStatus.FAILED
                : DeliveryStatus.PARTIAL_FAILED;
    }
}

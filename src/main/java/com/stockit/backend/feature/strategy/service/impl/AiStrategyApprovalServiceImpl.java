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
import com.stockit.backend.feature.strategy.approval.StrategyApprovalDeliveryStateService;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalPersistenceService;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.approval.StrategyReviewStatus;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalMessage;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalProperties;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalSender;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalDeliveryException;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.DeliveryStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.ReviewerDelivery;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.service.AiStrategyApprovalService;
import com.stockit.backend.feature.strategy.service.AiStrategyReviewerService;
import com.stockit.backend.feature.strategy.simulation.StrategySimulationContextStore;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

/** 최종 선택 DB 확정 후 Reviewer별 Teams 개인 채팅 전송을 조율한다. */
@Service
public class AiStrategyApprovalServiceImpl implements AiStrategyApprovalService {

    private final StrategyResultStore resultStore;
    private final StrategySimulationContextStore contextStore;
    private final AiStrategyReviewerService reviewerService;
    private final StrategyApprovalPersistenceService persistenceService;
    private final StrategyApprovalDeliveryStateService deliveryStateService;
    private final TeamsApprovalSender teamsApprovalSender;
    private final TeamsApprovalProperties properties;

    public AiStrategyApprovalServiceImpl(
            StrategyResultStore resultStore,
            StrategySimulationContextStore contextStore,
            AiStrategyReviewerService reviewerService,
            StrategyApprovalPersistenceService persistenceService,
            StrategyApprovalDeliveryStateService deliveryStateService,
            TeamsApprovalSender teamsApprovalSender,
            TeamsApprovalProperties properties
    ) {
        this.resultStore = resultStore;
        this.contextStore = contextStore;
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
        StrategyGenerationResult result = resultStore.find(strategyCaseId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.AI_STRATEGY_RESULT_EXPIRED
                ));
        StrategyGenerationResult.Option selectedOption = result.options().stream()
                .filter(option -> optionId.equals(
                        option.candidate().candidateId()
                ))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.AI_STRATEGY_CANDIDATE_NOT_FOUND
                ));
        StrategyCalculationContext context = contextStore.find(strategyCaseId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.AI_STRATEGY_RESULT_EXPIRED
                ));
        List<AiStrategyReviewerVO> reviewers = reviewerService.requireReviewers(
                organizationId, reviewerIds, properties.getMaxReviewers()
        );

        PreparedStrategyApproval prepared = persistenceService.prepare(
                strategyCaseId,
                actorId,
                organizationId,
                selectedOption,
                context,
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
            try {
                teamsApprovalSender.send(new TeamsApprovalMessage(
                        reviewer.getEmail(),
                        strategyCaseId,
                        prepared.caseName(),
                        context.sku().skuCode(),
                        context.sku().skuName(),
                        actorName,
                        selectedOption,
                        context
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
        return sent == 0 ? DeliveryStatus.FAILED : DeliveryStatus.PARTIAL_FAILED;
    }
}

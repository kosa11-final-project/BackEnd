package com.stockit.backend.feature.strategy.approval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.DeliveryStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.ReviewerDelivery;

/** 최초 전송과 재시도의 Reviewer별 선점·외부 호출·상태 전이를 공통 처리한다. */
@Service
public class StrategyTeamsDeliveryCoordinator {

    private static final String DELIVERY_KEY_PREFIX = "AI_STRATEGY_REVIEW:";

    private final StrategyApprovalDeliveryStateService deliveryStateService;
    private final TeamsApprovalSender teamsApprovalSender;
    private final TeamsApprovalProperties properties;

    public StrategyTeamsDeliveryCoordinator(
            StrategyApprovalDeliveryStateService deliveryStateService,
            TeamsApprovalSender teamsApprovalSender,
            TeamsApprovalProperties properties
    ) {
        this.deliveryStateService = deliveryStateService;
        this.teamsApprovalSender = teamsApprovalSender;
        this.properties = properties;
    }

    public AiStrategyTeamsRequestResponse deliver(
            PreparedTeamsDelivery prepared,
            Long actorId
    ) {
        List<ReviewerDelivery> deliveries = new ArrayList<>();
        for (TeamsApprovalRecipient recipient : prepared.recipients()) {
            deliveries.add(deliver(prepared.cardData(), recipient, actorId));
        }

        boolean alreadyReady = prepared.caseStatus()
                == StrategyCaseStatus.READY_TO_EXECUTE;
        boolean becameReady = deliveryStateService.markReadyIfComplete(
                prepared.strategyCaseId(), prepared.strategyOptionId(), actorId
        );
        boolean ready = alreadyReady || becameReady;
        return new AiStrategyTeamsRequestResponse(
                prepared.strategyCaseId(),
                prepared.selectedOptionId(),
                prepared.strategyOptionId(),
                prepared.finalSelectionId(),
                ready ? StrategyCaseStatus.READY_TO_EXECUTE
                        : StrategyCaseStatus.GENERATED,
                deliveryStatus(deliveries),
                deliveries
        );
    }

    private ReviewerDelivery deliver(
            TeamsApprovalCardData card,
            TeamsApprovalRecipient recipient,
            Long actorId
    ) {
        if (recipient.reviewStatus() == StrategyReviewStatus.SENT) {
            return delivery(recipient, StrategyReviewStatus.SENT, null);
        }
        boolean claimed = deliveryStateService.tryClaim(
                recipient.reviewRequestId(), actorId, properties.getClaimTimeout()
        );
        if (!claimed) {
            StrategyReviewStatus current = deliveryStateService.currentStatus(
                    recipient.reviewRequestId()
            );
            return delivery(recipient, current, null);
        }
        if (!recipient.deliverable()) {
            StrategyReviewStatus current = deliveryStateService.markFailed(
                    recipient.reviewRequestId(), actorId
            );
            return delivery(
                    recipient,
                    current,
                    current == StrategyReviewStatus.FAILED
                            ? "AI_STRATEGY_REVIEWER_NOT_FOUND" : null
            );
        }

        try {
            teamsApprovalSender.send(new TeamsApprovalMessage(
                    recipient.reviewRequestId(),
                    DELIVERY_KEY_PREFIX + recipient.reviewRequestId(),
                    recipient.email(),
                    card
            ));
            StrategyReviewStatus current = deliveryStateService.markSent(
                    recipient.reviewRequestId(), actorId
            );
            return delivery(recipient, current, null);
        } catch (TeamsApprovalDeliveryException exception) {
            StrategyReviewStatus current = deliveryStateService.markFailed(
                    recipient.reviewRequestId(), actorId
            );
            return delivery(
                    recipient,
                    current,
                    current == StrategyReviewStatus.FAILED
                            ? exception.getCode() : null
            );
        }
    }

    private static ReviewerDelivery delivery(
            TeamsApprovalRecipient recipient,
            StrategyReviewStatus status,
            String failureCode
    ) {
        return new ReviewerDelivery(
                recipient.reviewerId(),
                recipient.reviewerName(),
                recipient.email(),
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
        boolean inProgress = deliveries.stream().anyMatch(delivery ->
                delivery.deliveryStatus() == StrategyReviewStatus.SENDING
                        || delivery.deliveryStatus() == StrategyReviewStatus.PENDING);
        boolean failed = deliveries.stream().anyMatch(delivery ->
                delivery.deliveryStatus() == StrategyReviewStatus.FAILED);
        if (inProgress && !failed) {
            return DeliveryStatus.IN_PROGRESS;
        }
        return sent == 0 && !inProgress
                ? DeliveryStatus.FAILED
                : DeliveryStatus.PARTIAL_FAILED;
    }
}

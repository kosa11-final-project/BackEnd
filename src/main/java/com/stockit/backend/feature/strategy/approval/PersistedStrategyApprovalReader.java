package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PersistedApprovalAction;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PersistedApprovalHeader;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;

/** Redis가 만료된 뒤 Oracle 확정값만으로 Teams 재전송 입력을 복원한다. */
@Service
public class PersistedStrategyApprovalReader {

    private static final String CANDIDATE_ID_PREFIX = "candidateId=";

    private final StrategyApprovalMapper approvalMapper;

    public PersistedStrategyApprovalReader(StrategyApprovalMapper approvalMapper) {
        this.approvalMapper = approvalMapper;
    }

    @Transactional(readOnly = true)
    public PreparedTeamsDelivery read(Long strategyCaseId, Long organizationId) {
        PersistedApprovalHeader header = approvalMapper
                .selectPersistedApprovalHeader(strategyCaseId);
        if (header == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        if (!Objects.equals(
                organizationId, header.getRequesterOrganizationId()
        )) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (header.getCaseStatus() != StrategyCaseStatus.GENERATED
                && header.getCaseStatus() != StrategyCaseStatus.READY_TO_EXECUTE) {
            throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);
        }
        if (header.getFinalSelectionId() == null
                || header.getStrategyOptionId() == null
                || header.getPlannedStartDate() == null
                || header.getPlannedEndDate() == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);
        }

        List<PersistedApprovalAction> actions = approvalMapper
                .selectPersistedApprovalActions(header.getStrategyOptionId());
        List<StrategyApprovalRecords.ReviewRequestRecord> requests = approvalMapper
                .selectReviewRequestDeliveries(header.getStrategyOptionId());
        if (actions.isEmpty() || requests.isEmpty()) {
            throw new AppException(ErrorCode.DATABASE_ERROR);
        }

        String requesterName = requests.stream()
                .map(StrategyApprovalRecords.ReviewRequestRecord::getRequesterName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(header.getRequesterName());
        TeamsApprovalCardData card = new TeamsApprovalCardData(
                header.getCaseName(),
                header.getSkuCode(),
                header.getSkuName(),
                requesterName,
                header.getOptionName(),
                header.getRecommendationReason(),
                actions.stream()
                        .map(PersistedApprovalAction::getActionType)
                        .filter(Objects::nonNull)
                        .map(Enum::name)
                        .distinct()
                        .toList(),
                actions.stream()
                        .map(PersistedApprovalAction::getTargetSalesPointName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList(),
                header.getPlannedStartDate(),
                header.getPlannedEndDate(),
                header.getTargetQuantity(),
                actions.stream()
                        .map(PersistedApprovalAction::getDiscountRate)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList(),
                firstStrategyPrice(header, actions),
                header.getExpectedSalesQty(),
                header.getExpectedRevenue(),
                header.getTotalContributionMargin(),
                header.getExpectedRemainingQty()
        );
        List<TeamsApprovalRecipient> recipients = requests.stream()
                .map(request -> new TeamsApprovalRecipient(
                        request.getReviewRequestId(),
                        request.getReviewerId(),
                        request.getReviewerName(),
                        request.getReviewerEmail(),
                        request.getReviewStatus(),
                        "Y".equals(request.getReviewerActiveYn())
                                && !Boolean.TRUE.equals(request.getReviewerIsDeleted())
                ))
                .toList();
        if (header.getCaseStatus() == StrategyCaseStatus.READY_TO_EXECUTE
                && recipients.stream().anyMatch(recipient ->
                        recipient.reviewStatus() != StrategyReviewStatus.SENT)) {
            throw new AppException(ErrorCode.DATABASE_ERROR);
        }
        return new PreparedTeamsDelivery(
                strategyCaseId,
                candidateId(header.getConstraintText(), header.getStrategyOptionId()),
                header.getStrategyOptionId(),
                header.getFinalSelectionId(),
                header.getCaseStatus(),
                header.getPlannedStartDate(),
                card,
                recipients
        );
    }

    private static BigDecimal firstStrategyPrice(
            PersistedApprovalHeader header,
            List<PersistedApprovalAction> actions
    ) {
        if (header.getStrategyPrice() != null) {
            return header.getStrategyPrice();
        }
        return actions.stream()
                .map(PersistedApprovalAction::getStrategyPrice)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String candidateId(String constraintText, Long optionId) {
        if (constraintText != null) {
            for (String line : constraintText.split("\\n")) {
                if (line.startsWith(CANDIDATE_ID_PREFIX)) {
                    return line.substring(CANDIDATE_ID_PREFIX.length());
                }
            }
        }
        return "DB-OPTION-" + optionId;
    }
}

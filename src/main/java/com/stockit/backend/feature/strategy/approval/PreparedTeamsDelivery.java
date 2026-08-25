package com.stockit.backend.feature.strategy.approval;

import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;

/** DB Commit 이후 최초 전송과 Oracle 기반 재시도가 공유하는 불변 입력. */
public record PreparedTeamsDelivery(
        Long strategyCaseId,
        String selectedOptionId,
        Long strategyOptionId,
        Long finalSelectionId,
        StrategyCaseStatus caseStatus,
        LocalDate plannedStartDate,
        TeamsApprovalCardData cardData,
        List<TeamsApprovalRecipient> recipients
) {
    public PreparedTeamsDelivery {
        recipients = List.copyOf(recipients);
    }

    public boolean allSent() {
        return recipients.stream().allMatch(recipient ->
                recipient.reviewStatus() == StrategyReviewStatus.SENT);
    }
}

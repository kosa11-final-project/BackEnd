package com.stockit.backend.feature.strategy.approval;

import java.util.List;

import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;

/** DB 커밋이 끝난 뒤 외부 Teams 호출에 필요한 불변 입력. */
public record PreparedStrategyApproval(
        Long strategyCaseId,
        Long finalSelectionId,
        Long strategyOptionId,
        StrategyCaseStatus caseStatus,
        String caseName,
        ResolvedStrategySelection resolvedSelection,
        List<ReviewRequestRecord> reviewRequests
) {
    public PreparedStrategyApproval {
        reviewRequests = List.copyOf(reviewRequests);
    }
}

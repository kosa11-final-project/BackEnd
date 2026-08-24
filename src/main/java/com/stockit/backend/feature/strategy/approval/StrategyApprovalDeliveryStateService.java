package com.stockit.backend.feature.strategy.approval;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;

/** 외부 Teams 호출과 분리된 짧은 트랜잭션으로 전송 상태를 갱신한다. */
@Service
public class StrategyApprovalDeliveryStateService {

    private final StrategyApprovalMapper approvalMapper;

    public StrategyApprovalDeliveryStateService(
            StrategyApprovalMapper approvalMapper
    ) {
        this.approvalMapper = approvalMapper;
    }

    @Transactional
    public void markSent(Long reviewRequestId, Long actorId) {
        approvalMapper.updateReviewStatus(
                reviewRequestId, StrategyReviewStatus.SENT, actorId
        );
    }

    @Transactional
    public void markFailed(Long reviewRequestId, Long actorId) {
        approvalMapper.updateReviewStatus(
                reviewRequestId, StrategyReviewStatus.FAILED, actorId
        );
    }

    @Transactional
    public boolean markReadyIfComplete(
            Long strategyCaseId,
            Long strategyOptionId,
            Long actorId
    ) {
        return approvalMapper.markReadyToExecuteIfAllSent(
                strategyCaseId, strategyOptionId, actorId
        ) == 1;
    }
}

package com.stockit.backend.feature.strategy.service.impl;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.PersistedStrategyApprovalReader;
import com.stockit.backend.feature.strategy.approval.PreparedTeamsDelivery;
import com.stockit.backend.feature.strategy.approval.StrategyTeamsDeliveryCoordinator;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.service.AiStrategyApprovalRetryService;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

/** Oracle에 이미 확정된 전략의 미완료 Teams 전송만 복구한다. */
@Service
public class AiStrategyApprovalRetryServiceImpl
        implements AiStrategyApprovalRetryService {

    private final PersistedStrategyApprovalReader approvalReader;
    private final StrategyTeamsDeliveryCoordinator deliveryCoordinator;
    private final StrategyDateTimeProvider dateTimeProvider;

    public AiStrategyApprovalRetryServiceImpl(
            PersistedStrategyApprovalReader approvalReader,
            StrategyTeamsDeliveryCoordinator deliveryCoordinator,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.approvalReader = approvalReader;
        this.deliveryCoordinator = deliveryCoordinator;
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    public AiStrategyTeamsRequestResponse retry(
            Long strategyCaseId,
            Long actorId,
            Long organizationId
    ) {
        if (strategyCaseId == null || strategyCaseId <= 0
                || actorId == null || actorId <= 0
                || organizationId == null || organizationId <= 0) {
            throw new AppException(ErrorCode.AI_STRATEGY_INVALID_REQUEST);
        }
        PreparedTeamsDelivery prepared = approvalReader.read(
                strategyCaseId, organizationId
        );
        LocalDate businessDate = dateTimeProvider.now().toLocalDate();
        if (!prepared.allSent()
                && prepared.plannedStartDate().isBefore(businessDate)) {
            throw new AppException(ErrorCode.AI_STRATEGY_PERIOD_STALE);
        }
        return deliveryCoordinator.deliver(prepared, actorId);
    }
}

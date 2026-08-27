package com.stockit.backend.feature.strategy.service.impl;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.ResolvedStrategySelection;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.CaseRecord;
import com.stockit.backend.feature.strategy.approval.StrategySelectionExecutabilityValidator;
import com.stockit.backend.feature.strategy.approval.StrategySelectionResolver;
import com.stockit.backend.feature.strategy.dto.response.AiStrategySelectionValidationResponse;
import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;
import com.stockit.backend.feature.strategy.service.AiStrategySelectionValidationService;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;

/** Reviewer를 고르기 전에 최종안의 현재 실행 가능성을 DB 쓰기 없이 검증한다. */
@Service
public class AiStrategySelectionValidationServiceImpl
        implements AiStrategySelectionValidationService {

    private final StrategyApprovalMapper approvalMapper;
    private final StrategySelectionResolver selectionResolver;
    private final StrategySelectionExecutabilityValidator executabilityValidator;
    private final StrategyDateTimeProvider dateTimeProvider;

    public AiStrategySelectionValidationServiceImpl(
            StrategyApprovalMapper approvalMapper,
            StrategySelectionResolver selectionResolver,
            StrategySelectionExecutabilityValidator executabilityValidator,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.approvalMapper = approvalMapper;
        this.selectionResolver = selectionResolver;
        this.executabilityValidator = executabilityValidator;
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public AiStrategySelectionValidationResponse validate(
            Long strategyCaseId,
            String optionId,
            AdjustStrategySimulationCommand adjustedConditions,
            Long organizationId
    ) {
        if (strategyCaseId == null || strategyCaseId <= 0
                || optionId == null || optionId.isBlank()
                || organizationId == null || organizationId <= 0) {
            throw new AppException(ErrorCode.AI_STRATEGY_INVALID_REQUEST);
        }
        CaseRecord strategyCase = approvalMapper.selectCase(strategyCaseId);
        if (strategyCase == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        if (!Objects.equals(
                organizationId, strategyCase.getRequesterOrganizationId()
        )) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        ResolvedStrategySelection resolved = selectionResolver.resolve(
                strategyCaseId, optionId, adjustedConditions
        );
        executabilityValidator.validate(resolved, resolved.businessDate());
        return new AiStrategySelectionValidationResponse(
                strategyCaseId,
                resolved.option().candidate().candidateId(),
                true,
                resolved.inputSource(),
                resolved.targetQuantity(),
                resolved.option().candidate().startDate(),
                resolved.evaluationEndDate(),
                dateTimeProvider.now()
        );
    }
}

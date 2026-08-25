package com.stockit.backend.feature.strategy.service;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.mapper.AiStrategyCaseDetailMapper;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseDetailVO;

/** 상세 계산 결과를 사용하는 후속 작업의 Case 상태와 만료를 공통 검증한다. */
@Component
public class StrategyCaseLifecycleGuard {

    private final AiStrategyCaseDetailMapper detailMapper;
    private final StrategyDateTimeProvider dateTimeProvider;

    public StrategyCaseLifecycleGuard(
            AiStrategyCaseDetailMapper detailMapper,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.detailMapper = detailMapper;
        this.dateTimeProvider = dateTimeProvider;
    }

    public AiStrategyCaseDetailVO requireAdjustable(Long strategyCaseId) {
        AiStrategyCaseDetailVO strategyCase = requireResultBacked(strategyCaseId);
        if (strategyCase.getCaseStatus() != StrategyCaseStatus.GENERATED) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_READY);
        }
        return strategyCase;
    }

    /** 최초 선택과 Redis TTL 안의 동일 선택 멱등 재요청을 허용한다. */
    public AiStrategyCaseDetailVO requireSelectable(Long strategyCaseId) {
        AiStrategyCaseDetailVO strategyCase = requireResultBacked(strategyCaseId);
        if (strategyCase.getCaseStatus() != StrategyCaseStatus.GENERATED
                && strategyCase.getCaseStatus()
                != StrategyCaseStatus.READY_TO_EXECUTE) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_READY);
        }
        return strategyCase;
    }

    private AiStrategyCaseDetailVO requireResultBacked(Long strategyCaseId) {
        if (strategyCaseId == null || strategyCaseId <= 0) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        AiStrategyCaseDetailVO strategyCase = detailMapper.selectCaseDetail(
                strategyCaseId
        );
        if (strategyCase == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        if (strategyCase.getCaseStatus() == StrategyCaseStatus.EXPIRED) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        }
        if (strategyCase.getGenerationStage()
                != StrategyGenerationStage.COMPARISON_READY) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_READY);
        }
        if (strategyCase.getResultExpiresAt() == null
                || !strategyCase.getResultExpiresAt().isAfter(
                        dateTimeProvider.now()
                )
                || strategyCase.getResultCacheKey() == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        }
        return strategyCase;
    }
}

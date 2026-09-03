package com.stockit.backend.feature.strategy.approval;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

/** 최종 선택 조건이 생성 시점과 달라졌음을 구조화된 변경 내역과 함께 알린다. */
public class StrategyExecutionConditionChangedException extends AppException {

    public StrategyExecutionConditionChangedException(
            StrategyExecutionConditionChangedDetails details
    ) {
        super(
                ErrorCode.AI_STRATEGY_EXECUTION_CONDITION_CHANGED,
                ErrorCode.AI_STRATEGY_EXECUTION_CONDITION_CHANGED.getMessage(),
                details
        );
    }
}

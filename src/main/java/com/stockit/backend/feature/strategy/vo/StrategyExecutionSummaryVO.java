package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionSummaryVO {
    private long executionStrategyCount;
    private long inProgressStrategyCount;
    private long attentionStrategyCount;
    private long totalStrategyCount;
}

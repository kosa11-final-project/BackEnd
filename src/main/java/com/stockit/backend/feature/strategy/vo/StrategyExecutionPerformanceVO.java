package com.stockit.backend.feature.strategy.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionPerformanceVO {
    private Long performanceCount;
    private BigDecimal actualSalesQuantity;
    private BigDecimal actualRevenue;
    private BigDecimal actualContributionMargin;
    private BigDecimal actualRemainingQuantity;
    private BigDecimal movedQuantity;
    private BigDecimal disposedQuantity;
}

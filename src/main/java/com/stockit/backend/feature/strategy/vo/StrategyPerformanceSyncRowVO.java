package com.stockit.backend.feature.strategy.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyPerformanceSyncRowVO {
    private Long finalSelectionId;
    private Long strategyOptionId;
    private LocalDate performanceDate;
    private BigDecimal actualSalesQuantity;
    private BigDecimal actualRevenue;
    private BigDecimal actualContributionMargin;
    private BigDecimal actualRemainingQuantity;
}

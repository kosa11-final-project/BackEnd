package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationCostVO {
    private Long skuCostId;
    private BigDecimal unitCost;
}

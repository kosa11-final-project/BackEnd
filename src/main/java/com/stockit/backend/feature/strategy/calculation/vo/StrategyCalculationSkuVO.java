package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationSkuVO {
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String unitCode;
    private BigDecimal packageQuantity;
}
